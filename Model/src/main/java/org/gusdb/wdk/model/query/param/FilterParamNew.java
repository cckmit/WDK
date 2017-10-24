package org.gusdb.wdk.model.query.param;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.gusdb.fgputil.cache.ItemCache;
import org.gusdb.fgputil.cache.UnfetchableItemException;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.runner.SQLRunner;
import org.gusdb.fgputil.db.runner.SQLRunnerException;
import org.gusdb.fgputil.db.runner.SingleLongResultSetHandler;
import org.gusdb.wdk.cache.CacheMgr;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.user.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * The filter param is similar to a flatVocabParam in that it provides SQL suitable to embed in an IN clause.
 * The SQL returns a list of internal values, similar to flatVocabParam.
 * 
 * It is configured by two queries: ontologyQuery and metadataQuery. The former returns a tree of categories
 * and information describing them. The latter maps internal values to entries in the ontology (such as age)
 * and provides a value for the ontology entry (such as 18).
 * 
 * Both these queries can be dependent.
 * 
 * The input from the user is in the form of a set of filters. each filter specifies an ontology entry and one
 * or more values for it. The param returns all internal values from the metadata query that match all
 * supplied filters (i.e. the filtering is done on the backend).
 * 
 * the filter param also provides summary information to the client about the distribution of values for a
 * requested ontology entry.
 * 
 * The values returned are:
 * 
 * raw value: a JSON string of applied filters
 * 
 * stable value: same as raw value
 * 
 * signature: a checksum of the JSON string, but values are sorted
 * 
 * internal: SQL to return a set of internal values from the metadata query
 * 
 * @author steve
 */
public class FilterParamNew extends AbstractDependentParam {

  public static class OntologyCache extends ItemCache<String, Map<String, OntologyItem>> {}

  public static class MetadataNewCache extends ItemCache<String, Map<String, MetaDataItem>> {}

  public static class FilterParamNewCache extends ItemCache<String, FilterParamNewInstance> {}

  public static class FilterParamSummaryCounts {
    public long unfilteredCount = 0;
    public long filteredCount = 0;
    public long untransformedUnfilteredCount = 0;
    public long untransformedFilteredCount = 0;
  }

  // ontology query columns
  static final String COLUMN_ONTOLOGY_ID = "ontology_term_name";
  static final String COLUMN_PARENT_ONTOLOGY_ID = "parent_ontology_term_name";
  static final String COLUMN_DISPLAY_NAME = "display_name";
  static final String COLUMN_DESCRIPTION = "description";
  static final String COLUMN_TYPE = "type";
  static final String COLUMN_UNITS = "units";
  static final String COLUMN_PRECISION = "precision";
  static final String COLUMN_IS_RANGE = "is_range";

  // metadata query columns
  static final String COLUMN_INTERNAL = "internal"; // similar to the internal column in a flat vocab param

  private static final String FILTERED_IDS_MACRO = "##FILTERED_IDS##";

  private static final int FETCH_SIZE = 1000;

  private String metadataQueryRef;
  private Query metadataQuery;

  private WdkModelText _idTransformSql;

  private String ontologyQueryRef;
  private Query ontologyQuery;

  private String backgroundQueryRef;
  private Query backgroundQuery;

  private String filterDataTypeDisplayName;

  @Override
  public Set<String> getContainedQueryFullNames() {
    Set<String> names = new HashSet<String>();
    names.add(metadataQueryRef);
    names.add(ontologyQueryRef);
    names.add(backgroundQueryRef);
    return names;
  }

  private boolean _useIdTransformSqlForInternalValue = false;

  // remove non-terminal nodes with a single child
  private boolean trimMetadataTerms = true;

  public FilterParamNew() {
    // register handlers
    setHandler(new FilterParamNewHandler());
  }

  /**
   * @param param
   */
  public FilterParamNew(FilterParamNew param) {
    super(param);

    this.metadataQueryRef = param.metadataQueryRef;
    if (param.metadataQuery != null)
      this.metadataQuery = param.metadataQuery.clone();

    this._idTransformSql = param._idTransformSql;

    this.ontologyQueryRef = param.ontologyQueryRef;
    if (param.ontologyQuery != null)
      this.ontologyQuery = param.ontologyQuery.clone();

    this.backgroundQueryRef = param.backgroundQueryRef;
    if (param.backgroundQuery != null)
      this.backgroundQuery = param.backgroundQuery.clone();

    this.trimMetadataTerms = param.trimMetadataTerms;
    this._useIdTransformSqlForInternalValue = param._useIdTransformSqlForInternalValue;
    this.filterDataTypeDisplayName = param.filterDataTypeDisplayName;
  }

  @Override
  public Param clone() {
    return new FilterParamNew(this);
  }

  /**
   * @return the metadata query ref
   */
  public String getMetadataQueryRef() {
    return metadataQueryRef;
  }

  /**
   * @param metadataQueryRef
   *          the metadata query ref to set
   */
  public void setMetadataQueryRef(String metadataQueryRef) {
    this.metadataQueryRef = metadataQueryRef;
  }

  /**
   * @return the metadataQuery
   */
  public Query getMetadataQuery() {
    return metadataQuery;
  }

  public void setMetadataQuery(Query metadataQuery) {
    this.metadataQuery = metadataQuery;
  }

  public WdkModelText getIdTransformSql() {
    return _idTransformSql;
  }

  public void setIdTransformSql(WdkModelText idTransformSql) {
    this._idTransformSql = idTransformSql;
  }

  /**
   * @return the onotology Query Name
   */
  public String getOntologyQueryRef() {
    return ontologyQueryRef;
  }

  /**
   * @param ontologyQueryRef
   *          the ontologyName to set
   */
  public void setOntologyQueryRef(String ontologyQueryRef) {
    this.ontologyQueryRef = ontologyQueryRef;
  }

  /**
   * @return the ontologyQuery
   */
  public Query getOntologyQuery() {
    return ontologyQuery;
  }

  /**
   * @param ontologyQuery
   *          the ontologyQuery to set
   */
  public void setOntologyQuery(Query ontologyQuery) {
    this.ontologyQuery = ontologyQuery;
  }

  /**
   * @return the onotology Query Name
   */
  public String getBackgroundQueryRef() {
    return backgroundQueryRef;
  }

  /**
   * @param backgroundQueryRef
   *          the backgroundName to set
   */
  public void setBackgroundQueryRef(String backgroundQueryRef) {
    this.backgroundQueryRef = backgroundQueryRef;
  }

  /**
   * @return the backgroundQuery
   */
  public Query getBackgroundQuery() {
    return backgroundQuery;
  }

  /**
   * @param backgroundQuery
   *          the backgroundQuery to set
   */
  public void setBackgroundQuery(Query backgroundQuery) {
    this.backgroundQuery = backgroundQuery;
  }

  /**
   * @return the trimMetadataTerms
   */
  public boolean getTrimMetadataTerms() {
    return trimMetadataTerms;
  }

  public void setFilterDataTypeDisplayName(String filterDataTypeDisplayName) {
    this.filterDataTypeDisplayName = filterDataTypeDisplayName;
  }

  public String getFilterDataTypeDisplayName() {
    return filterDataTypeDisplayName;
  }

  public void setUseIdTransformSqlForInternalValue(boolean useIt) {
    this._useIdTransformSqlForInternalValue = useIt;
  }

  public boolean getUseIdTransformSqlForInternalValue() {
    return _useIdTransformSqlForInternalValue;
  }

  /**
   * @param trimMetadataTerms
   *          the metadataQuery to set
   */
  public void setTrimMetadataTerms(boolean trimMetadataTerms) {
    this.trimMetadataTerms = trimMetadataTerms;
  }

  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    super.resolveReferences(model);

    // resolve ontology query
    if (ontologyQueryRef != null) {

      // validate dependent params
      this.ontologyQuery = resolveDependentQuery(model, ontologyQueryRef, "ontology query");

      // validate columns
      Map<String, Column> columns = ontologyQuery.getColumnMap();
      String[] cols = { COLUMN_ONTOLOGY_ID, COLUMN_PARENT_ONTOLOGY_ID, COLUMN_DISPLAY_NAME,
          COLUMN_DESCRIPTION, COLUMN_TYPE, COLUMN_UNITS, COLUMN_PRECISION };
      for (String col : cols)
        if (!columns.containsKey(col))
          throw new WdkModelException("The ontologyQuery " + ontologyQueryRef + " in filterParam " +
              getFullName() + " must include column: " + col);
    }

    List<String> metadataCols = new ArrayList<String>(OntologyItemType.getTypedValueColumnNames());
    metadataCols.add(COLUMN_INTERNAL);

    // resolve background query
    if (backgroundQueryRef != null) {

      // validate dependent params
      this.backgroundQuery = resolveDependentQuery(model, backgroundQueryRef, "background query");

      // validate columns
      Map<String, Column> columns = backgroundQuery.getColumnMap();
      for (String col : metadataCols)
        if (!columns.containsKey(col))
          throw new WdkModelException("The backgroundQuery " + backgroundQueryRef + " in filterParam " +
              getFullName() + " must include column: " + col);
    }

    // resolve metadata query
    if (metadataQueryRef != null) {

      // validate dependent params
      this.metadataQuery = resolveDependentQuery(model, metadataQueryRef, "metadata query");

      // validate columns.
      Map<String, Column> columns = metadataQuery.getColumnMap();
      for (String col : metadataCols)
        if (!columns.containsKey(col))
          throw new WdkModelException("The metadata query " + metadataQueryRef + " in filterParam " +
              getFullName() + " must include column: " + col);
    }

    // validate optional id transform sql
    if (_idTransformSql != null) {
      if (!_idTransformSql.getText().contains(FILTERED_IDS_MACRO)) {
        throw new WdkModelException(
            "The id transform sql, " + _idTransformSql.getText() + ", in filterParam " + getFullName() +
                " must have SQL that contains the macro " + FILTERED_IDS_MACRO);
      }
    }
  }

  /**
   * We cache this because typically ontologies are sensitive only to the grossest dependent param (eg,
   * dataset), so will be reused across users.
   * 
   * @return <propertyName, <infoKey, infoValue>>, or null if metadataQuery is not specified
   * @throws WdkModelException
   */
  public Map<String, OntologyItem> getOntology(User user, Map<String, String> contextParamValues)
      throws WdkModelException {

    contextParamValues = ensureRequiredContext(user, contextParamValues);

    OntologyItemNewFetcher fetcher = new OntologyItemNewFetcher(ontologyQuery, contextParamValues, user);
    Map<String, OntologyItem> map = null;
    try {
      map = CacheMgr.get().getOntologyNewCache().getItem(fetcher.getCacheKey(), fetcher);
    }
    catch (UnfetchableItemException ex) {
      decodeException(ex);
    }
    return map;
  }

  /**
   * 
   * @param user
   * @param contextParamValues
   *          holds the depended param values (and possibly others)
   * @param appliedFilters
   *          (in stable value format)
   * @return { totalCount: number; filteredCount: number; }
   * @throws WdkModelException
   */
  public FilterParamSummaryCounts getTotalsSummary(User user, Map<String, String> contextParamValues,
      JSONObject appliedFilters) throws WdkModelException {

    contextParamValues = ensureRequiredContext(user, contextParamValues);

    /* GET UNFILTERED (BACKGROUND) COUNTS */
    // use background query if provided, else use metadata query

    // get base background query
    Query bgdQuery = backgroundQuery == null ? metadataQuery : backgroundQuery;
    String bgdSql;
    try {
      QueryInstance<?> queryInstance = bgdQuery.makeInstance(user, contextParamValues, true, 0,
          new HashMap<String, String>());
      bgdSql = queryInstance.getSql();
    }
    catch (WdkUserException e) {
      throw new WdkModelException(e);
    }

    // reduce it to a set of distinct internals
    // we know that each ontology_term_id has a full set of internals, so we just need to query
    // one ontology_term_id.
    String distinctInternalsSql = "SELECT distinct md." + COLUMN_INTERNAL + " FROM (" + bgdSql + ") md" +
        " WHERE md." + COLUMN_ONTOLOGY_ID + " IN (select " + COLUMN_ONTOLOGY_ID + " from (" + bgdSql +
        ") where rownum = 1)";

    // Set up counts store
    FilterParamSummaryCounts fpsc = new FilterParamSummaryCounts();

    // get untransformed unfiltered count
    String sql = "select count(*) from (" + distinctInternalsSql + ")";
    fpsc.untransformedUnfilteredCount = runCountSql(sql);

    // get transformed unfiltered count
    sql = "select count(*) from (" + transformIdSql(distinctInternalsSql) + ")";

    fpsc.unfilteredCount = runCountSql(sql);

    /* GET FILTERED COUNTS */
    // sql to find the filtered count
    FilterParamNewStableValue stableValue = new FilterParamNewStableValue(appliedFilters, this);
    String filteredInternalsSql = FilterParamNewHandler.getFilteredValue(user, stableValue,
        contextParamValues, this, metadataQuery);

    // get untransformed filtered count
    sql = "select count(*) as CNT from (" + filteredInternalsSql + ")";

    fpsc.untransformedFilteredCount = runCountSql(sql);

    // get transformed count
    sql = "select count(*) as CNT from (" + transformIdSql(filteredInternalsSql) + ")";

    fpsc.filteredCount = runCountSql(sql);

    return fpsc;
  }

  String transformIdSql(String idSql) {
    if (_idTransformSql == null)
      return idSql;
    return _idTransformSql.getText().replace(FILTERED_IDS_MACRO, idSql);
  }

  private long runCountSql(String sql) {
    return new SQLRunner(_wdkModel.getAppDb().getDataSource(), sql, "filter-param-counts").executeQuery(
        new SingleLongResultSetHandler()).getRetrievedValue();
  }

  /**
   * @param user
   * @param contextParamValues
   * @param ontologyId
   * @param appliedFilters
   * @param <T>
   *          The type of the values
   * @return a map from a value for this ontology term to the counts for that value.
   * @throws WdkModelException
   * TODO: MULTI-FILTER upgrade:  take a list of ontology terms, and return a map of maps, one per term.
   */
  public <T> Map<T, FilterParamSummaryCounts> getOntologyTermSummary(User user,
      Map<String, String> contextParamValues, OntologyItem ontologyItem, JSONObject appliedFilters,
      Class<T> ontologyItemClass) throws WdkModelException {

    contextParamValues = ensureRequiredContext(user, contextParamValues);

    FilterParamNewInstance paramInstance = createFilterParamNewInstance();

    /* GET UNFILTERED COUNTS */
    // use background query if provided, else use metadata query

    // get base bgd query
    Query bgdQuery = backgroundQuery == null ? metadataQuery : backgroundQuery;
    String bgdSql;
    try {
      QueryInstance<?> queryInstance = bgdQuery.makeInstance(user, contextParamValues, true, 0,
          new HashMap<String, String>());
      bgdSql = queryInstance.getSql();
    }
    catch (WdkUserException e) {
      throw new WdkModelException(e);
    }

    // limit it to our ontology_id
    String metadataSqlPerOntologyId = "SELECT mq.* FROM (" + bgdSql + ") mq WHERE mq." + COLUMN_ONTOLOGY_ID +
        " = ?";

    // read into a map of internal -> value(s)
    Map<String, List<T>> unfiltered = getMetaData(user, contextParamValues, ontologyItem, paramInstance,
        metadataSqlPerOntologyId, ontologyItemClass);

    // get histogram of those, stored in JSON
    Map<T, FilterParamSummaryCounts> summaryCounts = new HashMap<>();
    populateSummaryCounts(unfiltered, summaryCounts, false); // stuff in to 0th position in array

    /* GET FILTERED COUNTS */
    // get sql for the set internal ids that are pruned by the filters
    FilterParamNewStableValue stableValue = new FilterParamNewStableValue(appliedFilters, this);
    String internalSql = FilterParamNewHandler.getFilteredValue(user, stableValue, contextParamValues,
        this, getMetadataQuery());

    // use that set of ids to limit our ontology id's metadata
    String metadataSqlPerOntologyIdFiltered = metadataSqlPerOntologyId + " AND internal in (" + internalSql +
        ")";

    // read this filtered set into map of internal -> value(s)
    Map<String, List<T>> filtered = getMetaData(user, contextParamValues, ontologyItem, paramInstance,
        metadataSqlPerOntologyIdFiltered, ontologyItemClass);

    // add the filtered set into the histogram
    populateSummaryCounts(filtered, summaryCounts, true); // stuff in to 1st position in array

    return summaryCounts;
  }

  /**
   * stuff counts per ontology term value into json structure. first pair position is unfiltered, second is
   * filtered
   * 
   * @param metadataForOntologyId
   * @param metadataForOntologyId
   * @param summary
   */
  private <T> void populateSummaryCounts(Map<String, List<T>> metadataForOntologyId,
      Map<T, FilterParamSummaryCounts> summary, boolean filtered) {
    for (List<T> values : metadataForOntologyId.values()) {
      for (T value : values) {
        FilterParamSummaryCounts counts;
        if (summary.containsKey(value)) {
          counts = summary.get(value);
        }
        else {
          counts = new FilterParamSummaryCounts();
          summary.put(value, counts);
        }
        if (filtered)
          counts.filteredCount++;
        else
          counts.unfilteredCount++;
      }
    }
  }

  private void decodeException(UnfetchableItemException ex) throws WdkModelException {
    Throwable nestedException = ex.getCause();
    if (nestedException == null)
      throw new WdkModelException(ex.getMessage());
    if (nestedException instanceof WdkModelException)
      throw (WdkModelException) nestedException;
    throw new WdkModelException(nestedException);
  }

  /**
   * get an in-memory copy of meta data for a specified ontology_id
   * 
   * @param user
   * @param contextParamValues
   * @param cache
   *          the cache is needed, to make sure the contextParamValues are initialized correctly. (it is
   *          initialized when a cache is created.)
   * @param metaDataSql
   *          - sql that provides the meta data. has a single bind variable for ontology id
   * @return map from internal_id to that id's values.
   * @throws WdkModelException
   * TODO: MULTI-FILTER upgrade:  take a list of ontology terms, and return a map of maps, one per term.
   */
  private <T> Map<String, List<T>> getMetaData(User user, Map<String, String> contextParamValues,
      OntologyItem ontologyItem, FilterParamNewInstance cache, String metaDataSql, Class<T> ontologyItemClass)
      throws WdkModelException {

    String sql = "SELECT mq.* FROM (" + metaDataSql + ") mq WHERE mq." + COLUMN_ONTOLOGY_ID + " = ?";

    // run the composed sql, and get the metadata back
    Map<String, List<T>> metadata = new LinkedHashMap<>();
    PreparedStatement ps = null;
    ResultSet resultSet = null;
    DataSource dataSource = _wdkModel.getAppDb().getDataSource();
    try {
      ps = SqlUtils.getPreparedStatement(dataSource, sql);
      ps.setFetchSize(FETCH_SIZE);
      ps.setString(1, ontologyItem.getOntologyId());
      ps.setString(2, ontologyItem.getOntologyId());
      resultSet = ps.executeQuery();
      while (resultSet.next()) {
        String internal = resultSet.getString(COLUMN_INTERNAL);
        T value = OntologyItemType.resolveTypedValue(resultSet, ontologyItem, ontologyItemClass);

        // get list of values for this internal value, creating if not yet present
        List<T> values = metadata.get(internal);
        if (values == null) {
          values = new ArrayList<>();
          metadata.put(internal, values);
        }

        // add next value to the list
        values.add(value);
      }
    }
    catch (SQLException ex) {
      throw new WdkModelException(ex);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(resultSet, ps);
    }

    return metadata;
  }
  
  public Map<String, Set<String>> getDistinctMetaDataValues(User user,
      Map<String, String> contextParamValues) throws WdkModelException {
    
    contextParamValues = ensureRequiredContext(user, contextParamValues);

    Set<String> memberTerms = new HashSet<String>();
    Map<String, OntologyItem> ontology = getOntology(user, contextParamValues);
    for (OntologyItem ontologyItem : ontology.values()) if (ontologyItem.getType() != OntologyItemType.BRANCH && !ontologyItem.getIsRange()) memberTerms.add(ontologyItem.getOntologyId());
    return getDistinctMetaDataValues(user, contextParamValues, memberTerms, ontology, _wdkModel.getAppDb().getDataSource());
  }
  
  /**
   * return map of ontology field name to values (as strings), for the provided list of ontology terms
   * 
   * @param user
   * @param contextParamValues
   * @param ontologyTerms
   *          a list of ontology term to include in result.  
   * @return a map from field name -> a set of valid values. (We convert number values to strings)
   * @throws WdkModelException
   */
   Map<String, Set<String>> getDistinctMetaDataValues(User user,
      Map<String, String> contextParamValues, Set<String> ontologyTerms,
      Map<String, OntologyItem> ontology, DataSource dataSource) throws WdkModelException {

    // get bgd Query SQL
    Query bgdQuery = backgroundQuery == null ? metadataQuery : backgroundQuery;

    String metadataSql;
    try {
      QueryInstance<?> instance = bgdQuery.makeInstance(user, contextParamValues, true, 0,
          new HashMap<String, String>());
      metadataSql = instance.getSql();
    }
    catch (WdkUserException e) {
      throw new WdkModelException(e);
    }

    // find ontology terms used in our set of member filters
    String relevantOntologyTerms = "'" + ontologyTerms.stream().collect(
        Collectors.joining("', '")) + "'";

    // format SQL to select distinct term_name, value pairs
    String selectValuesStmt = OntologyItemType.getTypedValueColumnNames().stream().collect(
        Collectors.joining(", "));

    String filterSelectSql = "SELECT distinct " + FilterParamNew.COLUMN_ONTOLOGY_ID + ", " +
        selectValuesStmt + " FROM (" + metadataSql + ") md where " + FilterParamNew.COLUMN_ONTOLOGY_ID +
        " IN (" + relevantOntologyTerms + ")";

    // run sql, and stuff results into map of term -> values
    Map<String, Set<String>> distinctMetadataMembers = new HashMap<String, Set<String>>();
    try {
      new SQLRunner(dataSource, filterSelectSql, getFullName() + "__distinct_members").executeQuery(
          rs -> {
            while (rs.next()) {
              String field = rs.getString(FilterParamNew.COLUMN_ONTOLOGY_ID);
              OntologyItem ontologyItem = ontology.get(field);
              OntologyItemType type = ontologyItem.getType();
              Object value = OntologyItemType.resolveTypedValue(rs, ontologyItem, type.getJavaClass());
              String valueString = value == null? "NULL" : value.toString();
              if (!distinctMetadataMembers.containsKey(field)) distinctMetadataMembers.put(field, new HashSet<String>());
              distinctMetadataMembers.get(field).add(valueString);
            }
          });
      return distinctMetadataMembers;
    }
    catch (SQLRunnerException e) {
      throw new WdkModelException(e.getCause());
    }
  }


  public JSONObject getJsonValues(User user, Map<String, String> contextParamValues)
      throws WdkModelException {

    contextParamValues = ensureRequiredContext(user, contextParamValues);

    JSONObject jsParam = new JSONObject();

    try {
      // create json for the ontology
      JSONObject jsOntology = new JSONObject();
      Map<String, OntologyItem> ontologyMap = getOntology(user, contextParamValues);
      for (String itemName : ontologyMap.keySet()) {
        OntologyItem item = ontologyMap.get(itemName);
        JSONObject jsOntoItem = new JSONObject();
        jsOntoItem.put(COLUMN_PARENT_ONTOLOGY_ID, item.getParentOntologyId());
        jsOntoItem.put(COLUMN_DISPLAY_NAME, item.getDisplayName());
        jsOntoItem.put(COLUMN_DESCRIPTION, item.getDescription());
        jsOntoItem.put(COLUMN_TYPE, item.getType());
        jsOntoItem.put(COLUMN_UNITS, item.getUnits());
        jsOntoItem.put(COLUMN_PRECISION, item.getPrecision());

        jsOntology.put(itemName, jsOntoItem);
      }
      jsParam.put("ontology", jsOntology);
    }
    catch (JSONException ex) {
      throw new WdkModelException(ex);
    }
    return jsParam;
  }

  /**
   * remove invalid filters from stableValue. if stableValue empty, use default.
   */
  /*
   * protected String getValidStableValue(String stableValue) throws WdkModelException { try { if (stableValue
   * == null || stableValue.length() == 0) { JSONObject jsNewStableValue = new JSONObject();
   * jsNewStableValue.put(FilterParamHandler.FILTERS_KEY, new JSONArray()); return
   * JsonUtil.serialize(jsNewStableValue); }
   * 
   * JSONObject jsStableValue = new JSONObject(stableValue);
   * 
   * // need a cached version of uniqueMetadataStringValues query // for each filter in the input stableValue,
   * check that: // key is in the ontology // that value type matches ontology declared value type // if
   * string value, that value is in known string values
   * 
   * return jsStableValue.toString(); } catch (JSONException ex) { throw new WdkModelException(ex); } }
   */

  @Override
  public String getDefault() throws WdkModelException {
    String defaultValue = new JSONObject().put(FilterParamNewHandler.FILTERS_KEY, new JSONArray()).toString();
    return defaultValue;
  }

  /**
   * Default is always no filter... until there is pressure to change this.
   */
  @Override
  public String getDefault(User user, Map<String, String> contextParamValues) {
    String defaultValue = new JSONObject().toString();
    return defaultValue;
  }

  @Override
  public void setContextQuestion(Question question) throws WdkModelException {

    super.setContextQuestion(question);

    // also set context question in the metadata & ontology queries
    if (metadataQuery != null)
      metadataQuery.setContextQuestion(question);
    if (ontologyQuery != null)
      ontologyQuery.setContextQuestion(question);
  }

  /**
   * not sure this is used anyplace for real in the UI. Not used by question service.
   */
  @Override
  public String getBriefRawValue(Object rawValue, int truncateLength) throws WdkModelException {
    return rawValue.toString();
  }

  @Override
  protected void applySuggestion(ParamSuggestion suggest) {}

  /**
   * check each filter in stable value. add each invalid one to error message, and throw user exception.
   */
  @Override
  protected void validateValue(User user, String stableValueString, Map<String, String> contextParamValues)
      throws WdkModelException {

    FilterParamNewStableValue stableValue = new FilterParamNewStableValue(stableValueString, this);
    //String err = stableValue.validateSyntax();
    String err = stableValue.validateSyntaxAndSemantics(user, contextParamValues, _wdkModel.getAppDb().getDataSource());

    if (err != null) throw new WdkModelException(err);
  }

  @Override
  protected void appendChecksumJSON(JSONObject jsParam, boolean extra) throws JSONException {
    throw new UnsupportedOperationException(); // this method seems to go nowhere. TODO: remove its whole call
                                               // stack up to stepBean

  }

  private FilterParamNewInstance createFilterParamNewInstance() {
    return new FilterParamNewInstance(this);
  }

  @Override
  protected DependentParamInstance createDependentParamInstance(User user,
      Map<String, String> dependedParamValues) throws WdkModelException {
    return createFilterParamNewInstance();
  }

  @Override
  public String getSanityDefault(User user, Map<String, String> contextParamValues,
      SelectMode sanitySelectMode) {
    return getDefault(user, contextParamValues);
  }

  @Override
  public List<Query> getQueries() {
    List<Query> queries = new ArrayList<Query>();
    if (backgroundQuery != null) {
      queries.add(backgroundQuery);
    }
    if (metadataQuery != null) {
      queries.add(metadataQuery);
    }
    if (ontologyQuery != null) {
      queries.add(ontologyQuery);
    }
    return queries;
  }

}
