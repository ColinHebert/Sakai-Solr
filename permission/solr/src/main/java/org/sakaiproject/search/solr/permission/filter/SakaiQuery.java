package org.sakaiproject.search.solr.permission.filter;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.DelegatingCollector;
import org.apache.solr.search.ExtendedQueryBase;
import org.apache.solr.search.PostFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * PostFilter checking for each document if it's accessible to the user and if it should be returned as a valid result.
 *
 * @author Colin Hebert
 */
public class SakaiQuery extends ExtendedQueryBase implements PostFilter {
    private static final Logger logger = LoggerFactory.getLogger(SakaiQuery.class);
    private static final int EXTENDED_QUERY_COST_THRESHOLD = 100;
    private final String userId;
    private final String permissionService;
    private final HttpClient httpClient;

    public SakaiQuery(String userId, String permissionService) {
        this.userId = userId;
        this.permissionService = permissionService;
        this.httpClient = new DefaultHttpClient();
    }

    @Override
    public DelegatingCollector getFilterCollector(IndexSearcher searcher) {
        return new SakaiDelegatingCollector();
    }

    @Override
    public boolean getCache() {
        return false;
    }

    @Override
    public int getCost() {
        return Math.max(super.getCost(), EXTENDED_QUERY_COST_THRESHOLD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SakaiQuery that = (SakaiQuery) o;

        return permissionService.equals(that.permissionService) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + userId.hashCode();
        result = 31 * result + permissionService.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SakaiQuery{"
                + "permissionService='" + permissionService + '\''
                + ", userId='" + userId + '\''
                + '}';
    }

    private class SakaiDelegatingCollector extends DelegatingCollector {
        FieldCache.DocTerms referenceTerm;

        @Override
        public void collect(int doc) throws IOException {
            String reference = referenceTerm.getTerm(doc, new BytesRef()).utf8ToString();
            if (isUserAllowedToReadDocument(reference))
                super.collect(doc);
        }

        @Override
        public void setNextReader(AtomicReaderContext context) throws IOException {
            referenceTerm = FieldCache.DEFAULT.getTerms(context.reader(), "reference");
            super.setNextReader(context);
        }

        /**
         * Checks if the current user is allowed to read a given document.
         *
         * @param reference reference of the document to check.
         * @return true if the current user can read the document, false otherwise.
         */
        public boolean isUserAllowedToReadDocument(String reference) {
            boolean allowed = false;
            try {
                HttpGet httpget = new HttpGet(permissionService
                        + "?userId=" + URLEncoder.encode(userId, "UTF-8")
                        + "&reference=" + URLEncoder.encode(reference, "UTF-8"));
                HttpResponse response = httpClient.execute(httpget);
                allowed = Boolean.parseBoolean(EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
                logger.warn("Couldn't check if the user '" + userId + "' could access '" + reference + "'", e);
            }

            return allowed;
        }
    }
}
