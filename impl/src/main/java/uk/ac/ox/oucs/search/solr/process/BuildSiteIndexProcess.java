package uk.ac.ox.oucs.search.solr.process;

import org.apache.solr.client.solrj.SolrServer;
import org.sakaiproject.search.api.EntityContentProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ox.oucs.search.solr.ContentProducerFactory;

import java.util.Iterator;

/**
 * @author Colin Hebert
 */
public class BuildSiteIndexProcess implements SolrProcess {
    public static final int MAX_QUEUED_DOCUMENTS = 10;
    private static final Logger logger = LoggerFactory.getLogger(BuildSiteIndexProcess.class);
    private final SolrServer solrServer;
    private final ContentProducerFactory contentProducerFactory;
    private final String siteId;

    public BuildSiteIndexProcess(SolrServer solrServer, ContentProducerFactory contentProducerFactory, String siteId) {
        this.solrServer = solrServer;
        this.contentProducerFactory = contentProducerFactory;
        this.siteId = siteId;
    }

    @Override
    public void execute() {
        logger.info("Rebuilding the index for '" + siteId + "'");
        new CleanSiteIndexProcess(solrServer, siteId).execute();
        try {
            for (final EntityContentProducer entityContentProducer : contentProducerFactory.getContentProducers()) {
                Iterable<String> resourceNames = new Iterable<String>() {
                    @Override
                    public Iterator<String> iterator() {
                        return entityContentProducer.getSiteContentIterator(siteId);
                    }
                };

                int queued_documents = 0;
                for (String resourceName : resourceNames) {
                    new IndexDocumentProcess(solrServer, entityContentProducer, resourceName, false).execute();
                    if (queued_documents++ >= MAX_QUEUED_DOCUMENTS) {
                        solrServer.commit();
                        queued_documents = 0;
                    }
                }
            }
            solrServer.commit();
        } catch (Exception e) {
            logger.error("An exception occurred while rebuilding the index of '" + siteId + "'", e);
        }
    }
}
