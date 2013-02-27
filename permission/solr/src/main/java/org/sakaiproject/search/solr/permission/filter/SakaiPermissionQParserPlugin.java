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
    @Override
    public void init(NamedList args) {
    }

    @Override
    public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new QParser(qstr, localParams, params, req) {
            @Override
            public Query parse() throws SyntaxError {
                return new SakaiQuery(localParams.get("userId"), localParams.get("permissionService"));
            }
        };
    }
}
