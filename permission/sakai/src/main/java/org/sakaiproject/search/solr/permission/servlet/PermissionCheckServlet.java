package org.sakaiproject.search.solr.permission.servlet;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.search.api.EntityContentProducer;
import org.sakaiproject.search.api.SearchIndexBuilder;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Simple servlet allowing the solr server to check if a document can be accessed by a given user before returning
 * it as a result for a search query.
 * <p>
 * This allows to keep the paging system within solr and use the filtering system available in solr.
 * </p>
 *
 * @author Colin Hebert
 */
public class PermissionCheckServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(PermissionCheckServlet.class);
    /**
     * We should have used the {@link org.sakaiproject.search.producer.ContentProducerFactory} unfortunately, it isn't
     * a part of the API and would require to have the solr implementation as a dependency.
     */
    private SearchIndexBuilder searchIndexBuilder;
    private UserDirectoryService userDirectoryService;
    private SessionManager sessionManager;

    @Override
    public void init(ServletConfig config) throws ServletException {
        searchIndexBuilder = (SearchIndexBuilder) ComponentManager.get(SearchIndexBuilder.class);
        userDirectoryService = (UserDirectoryService) ComponentManager.get(UserDirectoryService.class);
        sessionManager = (SessionManager) ComponentManager.get(SessionManager.class);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        boolean hasAccess = false;
        String userId = req.getParameter("userId");
        String reference = req.getParameter("reference");
        ObjectOutputStream oos = null;

        try {
            oos = new ObjectOutputStream(resp.getOutputStream());
            hasAccess = checkPermission(userId, reference);
        } catch (Exception e) {
            logger.error("Couldn't check '" + userId + "' permission to access '" + reference + "'.", e);
        } finally {
            if (oos != null)
                try {
                    oos.writeBoolean(hasAccess);
                    oos.flush();
                } finally {
                    oos.close();
                }
        }
    }

    /**
     * Checks if a user has access to a referenced document withing solr.
     *
     * @param userId    user trying to access the document.
     * @param reference reference of the document.
     * @return true if the use can access the document, false otherwise.
     */
    private boolean checkPermission(String userId, String reference) {
        try {
            setCurrentUser(userId);
            EntityContentProducer contentProducer = searchIndexBuilder.newEntityContentProducer(reference);
            return contentProducer.canRead(reference);
        } catch (UserNotDefinedException e) {
            logger.warn("Couldn't set the current session for '" + userId + "'", e);
            return false;
        }
    }

    /**
     * Sets the current session to the user that will access the document.
     *
     * @param userId identifier of the user trying to access a resource.
     * @throws UserNotDefinedException thrown if the userId doesn't correspond to a real user.
     */
    private void setCurrentUser(String userId) throws UserNotDefinedException {
        String userEid = userDirectoryService.getUserEid(userId);
        Session session = sessionManager.getCurrentSession();
        session.setUserId(userId);
        session.setUserEid(userEid);
    }
}
