package uk.ac.ox.oucs.search.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.SolrPing;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.event.api.NotificationEdit;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.search.api.*;
import org.sakaiproject.search.model.SearchBuilderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ox.oucs.search.solr.filter.SearchItemFilter;
import uk.ac.ox.oucs.search.solr.notification.SearchNotificationAction;
import uk.ac.ox.oucs.search.solr.response.SolrSearchList;

import java.io.IOException;
import java.util.*;

/**
 * @author Colin Hebert
 */
public class SolrSearchService implements SearchService {
    private final Logger logger = LoggerFactory.getLogger(SolrSearchService.class);
    private SolrServer solrServer;
    private NotificationEdit notification;
    private SearchIndexBuilder searchIndexBuilder;
    private ContentProducerFactory contentProducerFactory;
    private List<String> triggerFunctions;
    private NotificationService notificationService;
    private SearchItemFilter searchItemFilter = new SearchItemFilter() {
        @Override
        public SearchResult filter(SearchResult result) {
            return result;
        }
    };

    /**
     * Register a notification action to listen to events and modify the search
     * index
     */
    public void init() {
        logger.debug("Register a notification to trigger indexation on new elements");
        // register a transient notification for resources
        notification = notificationService.addTransientNotification();

        // add all the functions that are registered to trigger search index modification
        notification.setFunction(SearchService.EVENT_TRIGGER_SEARCH);
        for (String function : triggerFunctions) {
            notification.addFunction(function);
        }

        // set the filter to any site related resource
        notification.setResourceFilter("/");

        // set the action
        notification.setAction(new SearchNotificationAction(searchIndexBuilder));
    }

    @Override
    public SearchList search(String searchTerms, List<String> contexts, int searchStart, int searchEnd) throws InvalidSearchQueryException {
        return search(searchTerms, contexts, searchStart, searchEnd, null, null);
    }

    @Override
    public SearchList search(String searchTerms, List<String> contexts, int start, int end, String filterName, String sorterName) throws InvalidSearchQueryException {
        try {
            SolrQuery query = new SolrQuery();

            query.setStart(start);
            query.setRows(end - start);
            query.setFields("*", "score");

            query.setHighlight(true).setHighlightSnippets(5);
            query.setParam("hl.useFastVectorHighlighter", true);
            query.setParam("hl.mergeContiguous", true);
            query.setParam("hl.fl", SearchService.FIELD_CONTENTS);

            query.setParam("tv", true);
            query.setParam("tv.fl", SearchService.FIELD_CONTENTS);
            query.setParam("tv.tf", true);

            if (contexts != null && !contexts.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append('+').append(SearchService.FIELD_CONTEXT).append(":");
                sb.append('(');
                for (Iterator<String> contextIterator = contexts.iterator(); contextIterator.hasNext(); ) {
                    sb.append('"').append(contextIterator.next()).append('"');
                    if (contextIterator.hasNext())
                        sb.append(" OR ");
                }
                sb.append(')');
                query.setFilterQueries(sb.toString());
            }

            logger.debug("Searching with Solr : " + searchTerms);
            query.setQuery(searchTerms);
            QueryResponse rsp = solrServer.query(query);
            return new SolrSearchList(rsp, searchItemFilter, contentProducerFactory);
        } catch (SolrServerException e) {
            throw new InvalidSearchQueryException("Failed to parse Query ", e);
        }
    }

    @Override
    public void registerFunction(String function) {
        logger.info("Register " + function + " as a trigger for the search service");
        notification.addFunction(function);
    }

    @Override
    public void reload() {
    }

    @Override
    public void refreshInstance() {
        searchIndexBuilder.refreshIndex();
    }

    @Override
    public void rebuildInstance() {
        searchIndexBuilder.rebuildIndex();
    }

    @Override
    public void refreshSite(String currentSiteId) {
        searchIndexBuilder.refreshIndex(currentSiteId);
    }

    @Override
    public void rebuildSite(String currentSiteId) {
        searchIndexBuilder.rebuildIndex(currentSiteId);
    }

    @Override
    public String getStatus() {
        try {
            logger.debug("Obtaining search server status");
            return String.valueOf(new SolrPing().process(solrServer).getStatus());
        } catch (Exception e) {
            return e.getLocalizedMessage();
        }
    }

    @Override
    public int getNDocs() {
        try {
            logger.debug("Obtaining the number of documents available on the server");
            QueryResponse rsp = solrServer.query(new SolrQuery().setRows(0).setQuery("*:*"));
            return (int) rsp.getResults().getNumFound();
        } catch (SolrServerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getPendingDocs() {
        return searchIndexBuilder.getPendingDocuments();
    }

    @Override
    public List<SearchBuilderItem> getAllSearchItems() {
        return Collections.emptyList();
    }

    @Override
    public List<SearchBuilderItem> getSiteMasterSearchItems() {
        return Collections.emptyList();
    }

    @Override
    public List<SearchBuilderItem> getGlobalMasterSearchItems() {
        return Collections.emptyList();
    }

    @Override
    public SearchStatus getSearchStatus() {
        return new SearchStatus() {

            @Override
            public String getLastLoad() {
                return "";
            }

            @Override
            public String getLoadTime() {
                return "";
            }

            @Override
            public String getCurrentWorker() {
                return "External indexing, no locks ";
            }

            @Override
            public String getCurrentWorkerETC() {
                return "List of current activity ";
            }

            @Override
            public List<Object[]> getWorkerNodes() {
                return Collections.singletonList(new Object[]{"NodeName", new Date(), "running status "});
            }

            @Override
            public String getNDocuments() {
                return String.valueOf(SolrSearchService.this.getNDocs());
            }

            @Override
            public String getPDocuments() {
                return String.valueOf(SolrSearchService.this.getPendingDocs());
            }
        };
    }

    @Override
    public boolean removeWorkerLock() {
        return true;
    }

    @Override
    public List<Object[]> getSegmentInfo() {
        return Collections.singletonList(new Object[]{"Index Segment Info is not implemented", "", ""});
    }

    @Override
    public void forceReload() {
    }

    @Override
    public TermFrequency getTerms(int documentId) throws IOException {
        throw new UnsupportedOperationException("Solr can't use documentId");
    }

    @Override
    public String searchXML(Map parameterMap) {
        throw new UnsupportedOperationException("Local search must be done against the solr server directly");
    }

    @Override
    public boolean isEnabled() {
        return ServerConfigurationService.getBoolean("search.enable", false);
    }

    @Override
    public String getDigestStoragePath() {
        return null;
    }

    @Override
    public String getSearchSuggestion(String searchString) {
        logger.debug("Search a suggestion for : " + searchString);
        try {
            ModifiableSolrParams params = new ModifiableSolrParams();
            params.set("qt", "/spell");
            params.set("q", searchString);
            params.set("spellcheck", true);
            params.set("spellcheck.collate", true);

            QueryResponse response = solrServer.query(params);
            SpellCheckResponse spellCheckResponse = response.getSpellCheckResponse();
            return spellCheckResponse.isCorrectlySpelled() ? null : spellCheckResponse.getCollatedResult();
        } catch (SolrServerException e) {
            logger.warn("Failed to obtain a suggestion", e);
            return null;
        }
    }

    //------------------------------------------------------------------------------------------
    //As far as I know, this implementation isn't diagnosable, so this is a dummy implementation
    //------------------------------------------------------------------------------------------
    @Override
    public void enableDiagnostics() {
    }

    @Override
    public void disableDiagnostics() {
    }

    @Override
    public boolean hasDiagnostics() {
        return false;
    }

    //-------------------------
    //Search services Accessors
    //-------------------------
    public void setSolrServer(SolrServer solrServer) {
        this.solrServer = solrServer;
    }

    public void setSearchIndexBuilder(SearchIndexBuilder searchIndexBuilder) {
        this.searchIndexBuilder = searchIndexBuilder;
    }

    public void setSearchItemFilter(SearchItemFilter searchItemFilter) {
        this.searchItemFilter = searchItemFilter;
    }

    public void setTriggerFunctions(List<String> triggerFunctions) {
        this.triggerFunctions = triggerFunctions;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setContentProducerFactory(ContentProducerFactory contentProducerFactory) {
        this.contentProducerFactory = contentProducerFactory;
    }
}
