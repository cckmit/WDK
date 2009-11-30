package org.gusdb.wdk.model.query;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.model.ModelSetI;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.ParamValuesSet;
import org.json.JSONException;

public class QuerySet extends WdkModelBase implements ModelSetI {

    private List<Query> queryList = new ArrayList<Query>();
    private Map<String, Query> queries = new LinkedHashMap<String, Query>();
    private String name;

    // for sanity testing
    private String queryType = "";
    public static final String TYPE_ATTRIBUTE = "attribute";
    public static final String TYPE_TABLE = "table";
    public static final String TYPE_VOCAB = "vocab";
    private boolean doNotTest = false;
    private List<ParamValuesSet> unexcludedDefaultParamValuesSets = new ArrayList<ParamValuesSet>();
    private ParamValuesSet defaultParamValuesSet;
    private List<WdkModelText> unexcludedTestRowCountSqls = new ArrayList<WdkModelText>();
    private String testRowCountSql;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Query getQuery(String name) throws WdkModelException {
        Query q = queries.get(name);
        if (q == null)
            throw new WdkModelException("Query Set " + getName()
                    + " does not include query " + name);
        return q;
    }

    public Object getElement(String name) {
        return queries.get(name);
    }

    public Query[] getQueries() {
        Query[] array = new Query[queries.size()];
        queries.values().toArray(array);
        return array;
    }

    public void setQueryType(String type) {
        this.queryType = type;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setDoNotTest(boolean doNotTest) {
        this.doNotTest = doNotTest;
    }

    public boolean getDoNotTest() {
        return doNotTest;
    }

    public void addDefaultParamValuesSet(ParamValuesSet paramValuesSet) {
        unexcludedDefaultParamValuesSets.add(paramValuesSet);
    }

    public ParamValuesSet getDefaultParamValuesSet() {
        return defaultParamValuesSet;
    }

    // sql that returns number of rows expected by all queries in this query set
    public void addTestRowCountSql(WdkModelText text) {
        unexcludedTestRowCountSqls.add(text);
    }

    public String getTestRowCountSql() {
        return testRowCountSql;
    }

    public boolean contains(String queryName) {
        return queries.containsKey(queryName);
    }

    public void addQuery(Query query) throws WdkModelException {
        query.setQuerySet(this);
        if (queryList != null) queryList.add(query);
        else queries.put(query.getName(), query);
    }

    public void resolveReferences(WdkModel model) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        for (Query query : queries.values()) {
            query.resolveReferences(model);
        }
    }

    /*
     * (non-Javadoc) do nothing
     * 
     * @see
     * org.gusdb.wdk.model.ModelSetI#setResources(org.gusdb.wdk.model.WdkModel)
     */
    public void setResources(WdkModel model) throws WdkModelException {}

    public String toString() {
        String newline = System.getProperty("line.separator");
        StringBuffer buf = new StringBuffer("QuerySet: name='" + name + "'");
        buf.append(newline);
        for (Query query : queries.values()) {
            buf.append(newline);
            buf.append(":::::::::::::::::::::::::::::::::::::::::::::");
            buf.append(newline);
            buf.append(query);
            buf.append("----------------");
            buf.append(newline);
        }
        return buf.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
     */
    @Override
    public void excludeResources(String projectId) throws WdkModelException {
        // exclude queries
        for (Query query : queryList) {
            if (query.include(projectId)) {
                query.excludeResources(projectId);
                String queryName = query.getName();
                if (queries.containsKey(queryName))
                    throw new WdkModelException("Query named " + queryName
                            + " already exists in query set " + getName());
                queries.put(queryName, query);
            }
        }
        queryList = null;

        // exclude paramValuesSets
        for (ParamValuesSet paramValuesSet : unexcludedDefaultParamValuesSets) {
            if (paramValuesSet.include(projectId)) {
                if (defaultParamValuesSet != null)
                    throw new WdkModelException(
                            "Duplicate <defaultTestParamValues> included in query set "
                                    + getName() + " for projectId " + projectId);
                defaultParamValuesSet = paramValuesSet;

            }
        }

        // exclude textRowCountSqls
        for (WdkModelText text : unexcludedTestRowCountSqls) {
            if (text.include(projectId)) {
                if (testRowCountSql != null)
                    throw new WdkModelException(
                            "Duplicate <testRowCountSql> included in query set "
                                    + getName() + " for projectId " + projectId);
                testRowCountSql = text.getText();

            }
        }
    }
    // ///////////////////////////////////////////////////////////////
    // ///// protected
    // ///////////////////////////////////////////////////////////////

}
