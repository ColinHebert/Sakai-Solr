package org.sakaiproject.search.solr.permission.filter;

import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.SyntaxError;

/**
 * QParser plugin allowing to search documents by permission in Sakai.
 *
 * @author Colin Hebert
 */
public class SakaiPermissionQParserPlugin extends QParserPlugin {
    private String permissionService;

    @Override
    public void init(NamedList initParams) {
        permissionService = (String) initParams.get("permissionService");
        if (permissionService == null) {
            throw new IllegalArgumentException("The property 'permissionService' must be set "
                    + "for the query parser SakaiPermissionQParserPlugin.");
        }
    }

    @Override
    public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new QParser(qstr, localParams, params, req) {
            private String userId = localParams.get("userId");

            @Override
            public Query parse() throws SyntaxError {
                return new SakaiQuery(userId, permissionService);
            }
        };
    }
}
