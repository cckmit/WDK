package org.gusdb.wdk.model;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.AutoCloseableList;
import org.gusdb.fgputil.IoUtil;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.fgputil.db.slowquery.QueryLogger;
import org.gusdb.fgputil.events.Event;
import org.gusdb.fgputil.events.EventListener;
import org.gusdb.fgputil.events.Events;
import org.gusdb.fgputil.events.ListenerExceptionEvent;
import org.gusdb.fgputil.runtime.InstanceManager;
import org.gusdb.fgputil.runtime.Manageable;
import org.gusdb.wdk.model.analysis.StepAnalysis;
import org.gusdb.wdk.model.analysis.StepAnalysisPlugins;
import org.gusdb.wdk.model.answer.single.SingleRecordQuestion;
import org.gusdb.wdk.model.config.ModelConfig;
import org.gusdb.wdk.model.config.ModelConfigAppDB;
import org.gusdb.wdk.model.config.ModelConfigUserDB;
import org.gusdb.wdk.model.config.ModelConfigUserDatasetStore;
import org.gusdb.wdk.model.config.QueryMonitor;
import org.gusdb.wdk.model.dataset.DatasetFactory;
import org.gusdb.wdk.model.dbms.ConnectionContainer;
import org.gusdb.wdk.model.dbms.ResultFactory;
import org.gusdb.wdk.model.filter.FilterSet;
import org.gusdb.wdk.model.ontology.EuPathCategoriesFactory;
import org.gusdb.wdk.model.ontology.Ontology;
import org.gusdb.wdk.model.ontology.OntologyFactory;
import org.gusdb.wdk.model.ontology.OntologyFactoryImpl;
import org.gusdb.wdk.model.query.BooleanQuery;
import org.gusdb.wdk.model.query.QuerySet;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.param.ParamSet;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.question.QuestionSet;
import org.gusdb.wdk.model.question.SearchCategory;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordClassSet;
import org.gusdb.wdk.model.user.BasketFactory;
import org.gusdb.wdk.model.user.FavoriteFactory;
import org.gusdb.wdk.model.user.StepFactory;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.model.user.UserFactory;
import org.gusdb.wdk.model.user.analysis.StepAnalysisFactory;
import org.gusdb.wdk.model.user.analysis.StepAnalysisFactoryImpl;
import org.gusdb.wdk.model.user.analysis.UnconfiguredStepAnalysisFactory;
import org.gusdb.wdk.model.user.dataset.UserDatasetFactory;
import org.gusdb.wdk.model.user.dataset.UserDatasetStore;
import org.gusdb.wdk.model.xml.XmlQuestionSet;
import org.gusdb.wdk.model.xml.XmlRecordClassSet;
import org.xml.sax.SAXException;

/**
 * The top level WdkModel object provides a facade to access all the resources and functionalities provided by
 * WDK. Furthermore, it is also an in-memory representation of the whole WDK model.
 */
public class WdkModel implements ConnectionContainer, Manageable<WdkModel>, AutoCloseable {

  private static final Logger LOG = Logger.getLogger(WdkModel.class);

  public static final String WDK_VERSION = "2.9.0";
  public static final String USER_SCHEMA_VERSION = "5";

  public static final String DB_INSTANCE_APP = "APP";
  public static final String DB_INSTANCE_USER = "USER";

  public static final String INDENT = "  ";

  /**
   * Convenience method for constructing a model from the configuration information.
   * 
   * @throws WdkModelException
   *           if unable to construct model
   */
  public static WdkModel construct(String projectId, String gusHome) throws WdkModelException {
    return InstanceManager.getInstance(WdkModel.class, gusHome, projectId);
  }

  private String _gusHome;
  private ModelConfig _modelConfig;
  private String _projectId;
  private long _startupTime;

  private DatabaseInstance appDb;
  private DatabaseInstance userDb;
  private UserDatasetStore userDatasetStore;

  private List<QuerySet> querySetList = new ArrayList<>();
  private Map<String, QuerySet> querySets = new LinkedHashMap<>();

  private List<ParamSet> paramSetList = new ArrayList<>();
  private Map<String, ParamSet> paramSets = new LinkedHashMap<>();

  private List<RecordClassSet> recordClassSetList = new ArrayList<>();
  private Map<String, RecordClassSet> recordClassSets = new LinkedHashMap<>();

  private List<QuestionSet> questionSetList = new ArrayList<>();
  private Map<String, QuestionSet> questionSets = new LinkedHashMap<>();

  private Map<String, ModelSetI<? extends WdkModelBase>> allModelSets = new LinkedHashMap<>();

  private List<GroupSet> groupSetList = new ArrayList<GroupSet>();
  private Map<String, GroupSet> groupSets = new LinkedHashMap<>();

  private List<XmlQuestionSet> xmlQuestionSetList = new ArrayList<>();
  private Map<String, XmlQuestionSet> xmlQuestionSets = new LinkedHashMap<>();

  private List<XmlRecordClassSet> xmlRecordClassSetList = new ArrayList<>();
  private Map<String, XmlRecordClassSet> xmlRecordClassSets = new LinkedHashMap<>();

  private List<FilterSet> filterSetList = new ArrayList<>();
  private Map<String, FilterSet> filterSets = new LinkedHashMap<>();

  private Map<String, String> _questionUrlSegmentMap = new HashMap<>();
  private Map<String, String> _recordClassUrlSegmentMap = new HashMap<>();
  
  private List<WdkModelName> wdkModelNames = new ArrayList<WdkModelName>();
  private String displayName;
  private String version; // use default version
  private String releaseDate;

  private List<WdkModelText> introductions = new ArrayList<WdkModelText>();
  private String _introduction;

  private List<MacroDeclaration> macroList = new ArrayList<MacroDeclaration>();
  private Set<String> modelMacroSet = new LinkedHashSet<String>();
  private Set<String> jspMacroSet = new LinkedHashSet<String>();
  private Set<String> perlMacroSet = new LinkedHashSet<String>();

  private ResultFactory resultFactory;

  private Map<String, String> properties;

  private UIConfig uiConfig = new UIConfig();

  private ExampleStratsAuthor exampleStratsAuthor;

  private StepAnalysisPlugins stepAnalysisPlugins;

  /**
   * xmlSchemaURL is used by the XmlQuestions. This is the only place where XmlQuestion can find it.
   */
  private URL xmlSchemaURL;

  private File xmlDataDir;

  private UserFactory userFactory;
  private StepFactory stepFactory;
  private DatasetFactory datasetFactory;
  private BasketFactory basketFactory;
  private FavoriteFactory favoriteFactory;
  private StepAnalysisFactory stepAnalysisFactory;
  private UserDatasetFactory userDatasetFactory;

  private List<PropertyList> defaultPropertyLists = new ArrayList<PropertyList>();
  private Map<String, String[]> defaultPropertyListMap = new LinkedHashMap<String, String[]>();

  private List<SearchCategory> categoryList = new ArrayList<SearchCategory>();
  private Map<String, SearchCategory> categoryMap = new LinkedHashMap<String, SearchCategory>();
  private Map<String, SearchCategory> rootCategoryMap = new LinkedHashMap<String, SearchCategory>();

  private List<OntologyFactoryImpl> ontologyFactoryList = new ArrayList<>();
  private Map<String, OntologyFactory> ontologyFactoryMap = new LinkedHashMap<>();
  private EuPathCategoriesFactory eupathCategoriesFactory = null;
  private String categoriesOntologyName = null;

  private String secretKey;

  private ReentrantLock systemUserLock = new ReentrantLock();
  private User systemUser;

  private String buildNumber;

  // unfortunately this must be public to fit in Manageable framework
  public WdkModel() {
    // add default sets
    try {
      addFilterSet(FilterSet.getWdkFilterSet());
    }
    catch (WdkModelException ex) {
      throw new WdkRuntimeException(ex);
    }
  }

  @Override
  public WdkModel getInstance(String projectId, String gusHome) throws WdkModelException {
    Date now = new Date();
    LOG.info("Constructing WDK Model for " + projectId + " with GUS_HOME=" + gusHome);
    LOG.info("WDK Model constructed by class: " + getCallingClass());
    LOG.info("Startup date " + now + " [" + now.getTime() + "]");

    startEvents();
    try {
      ModelXmlParser parser = new ModelXmlParser(gusHome);
      WdkModel wdkModel = parser.parseModel(projectId);
      wdkModel.setStartupTime(now.getTime());
      wdkModel.checkSchema();
      wdkModel.checkTmpDir();
      LOG.info("WDK Model construction complete.");
      return wdkModel;
    }
    catch (Exception ex) {
      LOG.error("Exception occurred while loading model.", ex);
      throw new WdkModelException(ex);
    }
  }

  /**
   * Starts events framework and listens for exceptions thrown by event handlers, logging them
   */
  private void startEvents() {
    Events.init();
    Events.subscribe(new EventListener() {
      @Override
      public void eventTriggered(Event event) throws Exception {
        ListenerExceptionEvent errorEvent = (ListenerExceptionEvent)event;
        LOG.error("Error while processing event: " +
            errorEvent.getEvent().getClass().getName(), errorEvent.getException());
      }
    }, ListenerExceptionEvent.class);
  }

  private static String getCallingClass() {
    final int stacktraceOffset = 6;
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    if (stackTrace.length == 0) return "unknown";
    int callIndex = (stackTrace.length <= stacktraceOffset ?
        stackTrace.length - 1 : stacktraceOffset);
    return stackTrace[callIndex].getClassName();
  }

  private void checkSchema() throws WdkModelException {
    // verify the user schema
    _modelConfig.getUserDB().checkSchema(this);
  }

  private void checkTmpDir() throws WdkModelException {
    String configuredDir = _modelConfig.getWdkTempDir();
    LOG.info("Checking configured temp dir: " + configuredDir);
    Path wdkTempDir = Paths.get(configuredDir);
    if (Files.exists(wdkTempDir) && Files.isDirectory(wdkTempDir) &&
        Files.isReadable(wdkTempDir) && Files.isWritable(wdkTempDir)) {
      return;
    }
    try {
      LOG.info("Temp dir does not exist or has insufficient permissions.  Trying to remedy...");
      Files.createDirectories(wdkTempDir);
      IoUtil.openPosixPermissions(wdkTempDir);
      LOG.info("Temp dir created at: " + wdkTempDir.toAbsolutePath());
    }
    catch (IOException e) {
      throw new WdkModelException("Unable to create WDK temp directory [" +
          configuredDir + "] and/or set open permissions", e);
    }
  }

  public static ModelConfig getModelConfig(String projectId, String gusHome) throws WdkModelException {
    try {
      ModelXmlParser parser = new ModelXmlParser(gusHome);
      return parser.getModelConfig(projectId);
    }
    catch (IOException | SAXException e) {
      throw new WdkModelException("Unable to read model config for gusHome '" + gusHome + "', projectId '" +
          projectId + "'", e);
    }
  }

  /**
   * @param questionFullName question's full name (two-part name)
   * @return question with the passed name
   * @throws WdkModelException if unable to resolve name to question
   */
  public Question getQuestion(String questionFullName) throws WdkModelException {
    // special case to fetch a single record of a recordClass (by primary keys)
    if (SingleRecordQuestion.isSingleQuestionName(questionFullName, this)){
      return new SingleRecordQuestion(questionFullName, this);
    }
    Reference r = new Reference(questionFullName);
    QuestionSet ss = getQuestionSet(r.getSetName());
    return ss.getQuestion(r.getElementName());
  }

  public Question[] getQuestions(RecordClass recordClass) {
    String rcName = recordClass.getFullName();
    List<Question> questions = new ArrayList<Question>();
    for (QuestionSet questionSet : questionSets.values()) {
      for (Question question : questionSet.getQuestions()) {
        if (question.getRecordClass().getFullName().equals(rcName))
          questions.add(question);
      }
    }
    Question[] array = new Question[questions.size()];
    questions.toArray(array);
    return array;
  }

  public RecordClass getRecordClass(String recordClassReference) throws WdkModelException {
    Reference r = new Reference(recordClassReference);
    RecordClassSet rs = getRecordClassSet(r.getSetName());
    return rs.getRecordClass(r.getElementName());
  }

  public ResultFactory getResultFactory() {
    return resultFactory;
  }

  public void addWdkModelName(WdkModelName wdkModelName) {
    this.wdkModelNames.add(wdkModelName);
  }

  /**
   * @return Returns the version.
   */
  public String getVersion() {
    return version;
  }

  public String getDisplayName() {
    return displayName;
  }

  private void setStartupTime(long startupTime) {
    _startupTime = startupTime;
  }

  public long getStartupTime() {
    return _startupTime;
  }

  public void addIntroduction(WdkModelText introduction) {
    introductions.add(introduction);
  }

  public String getIntroduction() {
    return _introduction;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public String getCategoriesOntologyName() {
    return categoriesOntologyName;
  }

  public void setProperties(Map<String, String> properties, Set<String> replacedMacros)
      throws WdkModelException {
    // make sure all the declared model macros are present
    for (String macro : modelMacroSet) {
      // macro not provided, error
      if (!properties.containsKey(macro))
        throw new WdkModelException("Required model macro '" + macro +
            "' is not defined in the model.prop file");
      // macro provided but not used, warning, but not error
      if (!replacedMacros.contains(macro))
        LOG.warn("The model macro '" + macro + "' is never used in" + " the model xml files.");
    }
    // make sure all the declared jsp macros are present
    for (String macro : jspMacroSet) {
      if (!properties.containsKey(macro))
        throw new WdkModelException("Required jsp macro '" + macro +
            "' is not defined in the model.prop file");
    }
    // make sure all the declared perl macros are present
    for (String macro : perlMacroSet) {
      if (!properties.containsKey(macro))
        throw new WdkModelException("Required perl macro '" + macro +
            "' is not defined in the model.prop file");
    }
    this.properties = properties;
  }

  // RecordClass Sets

  public RecordClassSet getRecordClassSet(String recordClassSetName) throws WdkModelException {

    if (!recordClassSets.containsKey(recordClassSetName)) {
      String err = "WDK Model " + _projectId + " does not contain a recordClass set with name " +
          recordClassSetName;
      throw new WdkModelException(err);
    }
    return recordClassSets.get(recordClassSetName);
  }
  
  // Start CWL 29JUN2016
  /**
   * Used to determine whether a record class set exists for the given reference
   * @param recordClassReference
   * @return - true if the record class set exists and false otherwise.
   * @throws WdkModelException
   */
  public boolean isExistsRecordClassSet(String recordClassReference) throws WdkModelException {
    Reference r = new Reference(recordClassReference);
    return recordClassSets.containsKey(r.getSetName());
  }
  // End CWL 29JUN2016

  public RecordClassSet[] getAllRecordClassSets() {
    RecordClassSet sets[] = new RecordClassSet[recordClassSets.size()];
    recordClassSets.values().toArray(sets);
    return sets;
  }

  // Query Sets

  public QuerySet getQuerySet(String setName) throws WdkModelException {
    if (!querySets.containsKey(setName)) {
      String err = "WDK Model " + _projectId + " does not contain a query set with name " + setName;
      throw new WdkModelException(err);
    }
    return querySets.get(setName);
  }

  public boolean hasQuerySet(String setName) {
    return querySets.containsKey(setName);
  }

  public QuerySet[] getAllQuerySets() {
    QuerySet sets[] = new QuerySet[querySets.size()];
    querySets.values().toArray(sets);
    return sets;
  }

  public QuestionSet[] getAllQuestionSets() {
    QuestionSet sets[] = new QuestionSet[questionSets.size()];
    questionSets.values().toArray(sets);
    return sets;
  }

  // Question Sets
  public QuestionSet getQuestionSet(String setName) throws WdkModelException {
    if (!questionSets.containsKey(setName)) {
      String err = "WDK Model " + _projectId + " does not contain a Question set with name " + setName;
      throw new WdkModelException(err);
    }
    return questionSets.get(setName);
  }

  public boolean hasQuestionSet(String setName) {
    return questionSets.containsKey(setName);
  }

  public Map<String, QuestionSet> getQuestionSets() {
    Map<String, QuestionSet> sets = new LinkedHashMap<String, QuestionSet>();
    for (String setName : questionSets.keySet()) {
      sets.put(setName, questionSets.get(setName));
    }
    return sets;
  }

  public ParamSet getParamSet(String setName) throws WdkModelException {
    if (!paramSets.containsKey(setName)) {
      String err = "WDK Model " + _projectId + " does not contain a param set with name " + setName;
      throw new WdkModelException(err);
    }
    return paramSets.get(setName);
  }

  public ParamSet[] getAllParamSets() {
    ParamSet[] sets = new ParamSet[paramSets.size()];
    paramSets.values().toArray(sets);
    return sets;
  }

  public GroupSet[] getAllGroupSets() {
    GroupSet[] array = new GroupSet[groupSets.size()];
    groupSets.values().toArray(array);
    return array;
  }

  public GroupSet getGroupSet(String setName) throws WdkModelException {
    GroupSet groupSet = groupSets.get(setName);
    if (groupSet == null)
      throw new WdkModelException("The Model does not " + "have a groupSet named " + setName);
    return groupSet;
  }

  public FilterSet[] getAllFilterSets() {
    FilterSet[] array = new FilterSet[filterSets.size()];
    filterSets.values().toArray(array);
    return array;
  }

  public FilterSet getFilterSet(String setName) throws WdkModelException {
    FilterSet filterSet = filterSets.get(setName);
    if (filterSet == null)
      throw new WdkModelException("The Model does not " + "have a filterSet named " + setName);
    return filterSet;
  }

  public Question getBooleanQuestion(RecordClass recordClass) throws WdkModelException {
    // check if the boolean question already exists
    String qname = Question.BOOLEAN_QUESTION_PREFIX + recordClass.getFullName().replace('.', '_');
    QuestionSet internalSet = getQuestionSet(Utilities.INTERNAL_QUESTION_SET);

    Question booleanQuestion;
    if (internalSet.contains(qname)) {
      booleanQuestion = internalSet.getQuestion(qname);
    }
    else {
      booleanQuestion = new Question();
      booleanQuestion.setName(qname);
      booleanQuestion.setDisplayName("Combine " + recordClass.getDisplayName() + " results");
      booleanQuestion.setRecordClassRef(recordClass.getFullName());
      BooleanQuery booleanQuery = getBooleanQuery(recordClass);
      booleanQuestion.setQueryRef(booleanQuery.getFullName());
      booleanQuestion.excludeResources(_projectId);
      booleanQuestion.resolveReferences(this);

      internalSet.addQuestion(booleanQuestion);
    }
    return booleanQuestion;
  }

  public BooleanQuery getBooleanQuery(RecordClass recordClass) throws WdkModelException {
    // check if the boolean query already exists
    String queryName = BooleanQuery.getQueryName(recordClass);
    QuerySet internalQuerySet = getQuerySet(Utilities.INTERNAL_QUERY_SET);

    BooleanQuery booleanQuery;
    if (internalQuerySet.contains(queryName)) {
      booleanQuery = (BooleanQuery) internalQuerySet.getQuery(queryName);
    }
    else {
      booleanQuery = recordClass.getBooleanQuery();

      // make sure we create index on primary keys
      booleanQuery.setIndexColumns(recordClass.getIndexColumns());

      internalQuerySet.addQuery(booleanQuery);

      booleanQuery.excludeResources(_projectId);
      booleanQuery.resolveReferences(this);
      booleanQuery.setDoNotTest(true);
      booleanQuery.setIsCacheable(true); // cache the boolean query
    }
    return booleanQuery;
  }

  // ModelSetI's
  private <T extends ModelSetI<? extends WdkModelBase>> void addSet(T set, Map<String, T> setMap)
      throws WdkModelException {
    String setName = set.getName();
    if (allModelSets.containsKey(setName)) {
      String err = "WDK Model " + _projectId + " already contains a set with name " + setName;

      throw new WdkModelException(err);
    }
    setMap.put(setName, set);
    allModelSets.put(setName, set);
  }

  /**
   * Set whatever resources the model needs. It will pass them to its kids
   */
  public void setResources() throws WdkModelException {
    for (ModelSetI<? extends WdkModelBase> modelSet : allModelSets.values()) {
      modelSet.setResources(this);
    }
  }

  /**
   * This method should happen after the resolveReferences, since projectId is set by this method from
   * modelConfig <- I am a horrible comment, explore later (resolveReferences is called in this method)
   */
  public void configure(ModelConfig modelConfig) throws WdkModelException {

    // assign projectId
    String projectId = modelConfig.getProjectId().trim();
    if (projectId.length() == 0 || projectId.indexOf('\'') >= 0)
      throw new WdkModelException("The projectId/modelName cannot be " +
          "empty, and cannot have single quote in it: " + projectId);
    _projectId = projectId;
    _modelConfig = modelConfig;
    ModelConfigAppDB appDbConfig = modelConfig.getAppDB();
    ModelConfigUserDB userDbConfig = modelConfig.getUserDB();
    ModelConfigUserDatasetStore udsConfig= modelConfig.getUserDatasetStoreConfig();
    if (udsConfig != null) userDatasetStore = udsConfig.getUserDatasetStore();

    QueryLogger.initialize(modelConfig.getQueryMonitor());

    appDb = new DatabaseInstance(appDbConfig, DB_INSTANCE_APP, true);
    userDb = new DatabaseInstance(userDbConfig, DB_INSTANCE_USER, true);

    resultFactory = new ResultFactory(this);
    userFactory = new UserFactory(this);
    stepFactory = new StepFactory(this);
    datasetFactory = new DatasetFactory(this);
    basketFactory = new BasketFactory(this);
    favoriteFactory = new FavoriteFactory(this);
    stepAnalysisFactory = (stepAnalysisPlugins == null ?
        new UnconfiguredStepAnalysisFactory(this) :
        new StepAnalysisFactoryImpl(this));
    userDatasetFactory = new UserDatasetFactory(this);

    // exclude resources that are not used by this project
    excludeResources();

    // internal sets will be created if author hasn't define them
    createInternalSets();

    // it has to be called after internal sets are created, but before
    // recordClass references are resolved.
    addBasketReferences();

    // resolve references in the model objects
    resolveReferences();

    // create boolean questions
    createBooleanQuestions();
  }


  @Override
  public void close() {
    LOG.info("Releasing WDK Model resources...");
    stepAnalysisFactory.shutDown();
    releaseDb(appDb);
    releaseDb(userDb);
    Events.shutDown();
    LOG.info("WDK Model resources released.");
  }

  private static void releaseDb(DatabaseInstance db) {
    try {
      LOG.info("Releasing database resources for DB: " + db.getIdentifier());
      db.close();
    }
    catch (Exception e) {
      LOG.error("Exception caught while trying to shut down DB instance " + "with name '" + db.getIdentifier() +
          "'.  Ignoring.", e);
    }
  }

  private void addBasketReferences() throws WdkModelException {
    for (RecordClassSet rcSet : recordClassSets.values()) {
      for (RecordClass recordClass : rcSet.getRecordClasses()) {
        if (recordClass.isUseBasket()) {
          basketFactory.createAttributeQueryRef(recordClass);
          basketFactory.createRealtimeBasketQuestion(recordClass);
          basketFactory.createSnapshotBasketQuestion(recordClass);
          basketFactory.createBasketAttributeQuery(recordClass);
        }
      }
    }
  }

  public ModelConfig getModelConfig() {
    return _modelConfig;
  }

  public DatabaseInstance getAppDb() {
    return appDb;
  }

  public DatabaseInstance getUserDb() {
    return userDb;
  }

  public UserFactory getUserFactory() {
    return userFactory;
  }

  public StepFactory getStepFactory() {
    return stepFactory;
  }

  public StepAnalysisFactory getStepAnalysisFactory() {
    return stepAnalysisFactory;
  }

  public UserDatasetFactory getUserDatasetFactory() {
    return userDatasetFactory;
  }

  public Object resolveReference(String twoPartName) throws WdkModelException {
    String s = "Invalid reference '" + twoPartName + "'. ";

    // ensures <code>twoPartName</code> is formatted correctly
    Reference reference = new Reference(twoPartName);

    String setName = reference.getSetName();
    String elementName = reference.getElementName();

    ModelSetI<? extends WdkModelBase> set = allModelSets.get(setName);

    if (set == null) {
      String s3 = s + " There is no set called '" + setName + "'";
      throw new WdkModelException(s3);
    }
    Object element = set.getElement(elementName);
    if (element == null) {
      String s4 = s + " Set '" + setName + "' returned null for '" + elementName + "'";
      String s5 = s4 + "\n\nIf you are modifying or trying to access a strategy, your attempt failed because the strategy contains at least a step with an *obsolete search*. Please contact us with the name of the strategy or the link you tried to follow (should be available on your browser's location bar).";
      throw new WdkModelException(s5);
    }
    return element;
  }

  /**
   * Some elements within the set may refer to others by name. Resolve those references into real object
   * references.
   */
  private void resolveReferences() throws WdkModelException {
    // Since we use Map here, the order of the sets in allModelSets are
    // random. However, if QuestionSet is resolved before a RecordSet, and
    // it goes down to resolve: QuestionSet -> Question -> RecordClass, and
    // when we try to resolve the RecordClass, a copy of it has been put
    // into RecordSet yet not being resolved. That means the attribute won't
    // be compatible since one contains nothing.
    // Iterator modelSets = allModelSets.values().iterator();
    // while (modelSets.hasNext()) {
    // ModelSetI modelSet = (ModelSetI) modelSets.next();
    // modelSet.resolveReferences(this);
    // }

    // instead, we first resolve querySets, then recordSets, and then
    // paramSets, and last on questionSets
    for (GroupSet groupSet : groupSets.values()) {
      groupSet.resolveReferences(this);
    }
    for (FilterSet filterSet : filterSets.values()) {
      filterSet.resolveReferences(this);
    }
    for (QuerySet querySet : querySets.values()) {
      querySet.resolveReferences(this);
    }
    for (ParamSet paramSet : paramSets.values()) {
      paramSet.resolveReferences(this);
    }
    for (RecordClassSet recordClassSet : recordClassSets.values()) {
      recordClassSet.resolveReferences(this);
    }
    for (QuestionSet questionSet : questionSets.values()) {
      questionSet.resolveReferences(this);
    }
    // resolve references for xml record classes and questions
    for (XmlRecordClassSet rcSet : xmlRecordClassSets.values()) {
      rcSet.resolveReferences(this);
    }
    for (XmlQuestionSet qSet : xmlQuestionSets.values()) {
      qSet.resolveReferences(this);
    }
    for (SearchCategory category : this.categoryMap.values()) {
      category.resolveReferences(this);
      if (category.getParent() == null)
        rootCategoryMap.put(category.getName(), category);
    }

    // resolve ontology references and determine WDK Categories ontology
    OntologyFactoryImpl ontologyFactory;
    switch (this.ontologyFactoryMap.size()) {

      case 0:
        throw new WdkModelException(
            "At least one ontology element must be specified in WDK Model XML.");

      case 1:
        ontologyFactory = (OntologyFactoryImpl)this.ontologyFactoryMap.values().iterator().next();
        ontologyFactory.resolveReferences(this);
        ontologyFactory.setUseAsWdkCategories(true);
        this.categoriesOntologyName = ontologyFactory.getName();
        break;

      default: // more than one ontology
        String wdkCategoriesOntologyName = null;
        for (OntologyFactory ontology: this.ontologyFactoryMap.values()) {
          // cast as (known) implementation
          ontologyFactory = (OntologyFactoryImpl)ontology;
          ontologyFactory.resolveReferences(this);
          // make sure only one ontology is set to be the WDK categories ontology
          if (ontologyFactory.getUseAsWdkCategories()) {
            if (wdkCategoriesOntologyName == null) {
              wdkCategoriesOntologyName = ontologyFactory.getName();
            }
            else {
              throw new WdkModelException("More than one ontology [" +
                  wdkCategoriesOntologyName + ", " + ontologyFactory.getName() +
                  "] is specified as the WDK Categories Ontology.  Only one can be used.");
            }
          }
        }
        if (wdkCategoriesOntologyName == null) {
            throw new WdkModelException("You must specify an ontology as the WDK Categories " +
                "Ontology.  Use the 'useAsWdkCategories' flag in the ontology XML tag.");
        }
        this.categoriesOntologyName = wdkCategoriesOntologyName;
    }

    // comment out to use old categories
    if (!ontologyFactoryMap.isEmpty() && !getProjectId().equals("OrthoMCL")) eupathCategoriesFactory = new EuPathCategoriesFactory(this);
 }

  private void excludeResources() throws WdkModelException {
    // decide model name, display name, and version
    boolean hasModelName = false;
    for (WdkModelName wdkModelName : wdkModelNames) {
      if (wdkModelName.include(_projectId)) {
        if (hasModelName) {
          throw new WdkModelException("The model has more than one " + "<modelName> for project " + _projectId);
        }
        else {
          this.displayName = wdkModelName.getDisplayName();
          this.version = wdkModelName.getVersion();
          this.releaseDate = wdkModelName.getReleaseDate();
          this.buildNumber = wdkModelName.getBuildNumber();
          hasModelName = true;
        }
      }
    }
    wdkModelNames = null; // no more use of modelNames

    // decide the introduction
    boolean hasIntroduction = false;
    for (WdkModelText intro : introductions) {
      if (intro.include(_projectId)) {
        if (hasIntroduction) {
          throw new WdkModelException("The model has more than one " + "<introduction> for project " +
              _projectId);
        }
        else {
          _introduction = intro.getText();
          hasIntroduction = true;
        }
      }
    }
    introductions = null;

    // exclude the property list
    for (PropertyList propList : defaultPropertyLists) {
      if (propList.include(_projectId)) {
        String listName = propList.getName();
        if (defaultPropertyListMap.containsKey(listName)) {
          throw new WdkModelException("The model has more than one " + "defaultPropertyList \"" + listName +
              "\" for project " + _projectId);
        }
        else {
          propList.excludeResources(_projectId);
          defaultPropertyListMap.put(listName, propList.getValues());
        }
      }
    }
    defaultPropertyLists = null;

    // remove question sets
    for (QuestionSet questionSet : questionSetList) {
      if (questionSet.include(_projectId)) {
        questionSet.excludeResources(_projectId);
        addSet(questionSet, questionSets);
      }
    }
    questionSetList = null;

    // remove param sets
    for (ParamSet paramSet : paramSetList) {
      if (paramSet.include(_projectId)) {
        paramSet.excludeResources(_projectId);
        addSet(paramSet, paramSets);
      }
    }
    paramSetList = null;

    // remove query sets
    for (QuerySet querySet : querySetList) {
      if (querySet.include(_projectId)) {
        querySet.excludeResources(_projectId);
        addSet(querySet, querySets);
      }
    }
    querySetList = null;

    // remove record class sets
    for (RecordClassSet recordClassSet : recordClassSetList) {
      if (recordClassSet.include(_projectId)) {
        recordClassSet.excludeResources(_projectId);
        addSet(recordClassSet, recordClassSets);
      }
    }
    recordClassSetList = null;

    // remove group sets
    for (GroupSet groupSet : groupSetList) {
      if (groupSet.include(_projectId)) {
        groupSet.excludeResources(_projectId);
        addSet(groupSet, groupSets);
      }
    }
    groupSetList = null;

    // remove xml question sets
    for (XmlQuestionSet xmlQSet : xmlQuestionSetList) {
      if (xmlQSet.include(_projectId)) {
        xmlQSet.excludeResources(_projectId);
        addSet(xmlQSet, xmlQuestionSets);
      }
    }
    xmlQuestionSetList = null;

    // remove xml record class sets
    for (XmlRecordClassSet xmlRSet : xmlRecordClassSetList) {
      if (xmlRSet.include(_projectId)) {
        xmlRSet.excludeResources(_projectId);
        addSet(xmlRSet, xmlRecordClassSets);
      }
    }
    xmlRecordClassSetList = null;

    // remove filter sets
    for (FilterSet filterSet : filterSetList) {
      if (filterSet.include(_projectId)) {
        filterSet.excludeResources(_projectId);
        addSet(filterSet, filterSets);
      }
    }
    filterSetList = null;

    // exclude categories
    for (SearchCategory category : this.categoryList) {
      if (category.include(_projectId)) {
        String name = category.getName();
        if (categoryMap.containsKey(name))
          throw new WdkModelException("The category name '" + name + "' is duplicated");
        category.excludeResources(_projectId);
        categoryMap.put(name, category);
      }
    }
    categoryList = null;

    // exclude ontologies
    for (OntologyFactoryImpl ontology : this.ontologyFactoryList) {
      if (ontology.include(_projectId)) {
        String name = ontology.getName();
        if (ontologyFactoryMap.containsKey(name))
          throw new WdkModelException("The ontology name '" + name + "' is duplicated");
        ontology.excludeResources(_projectId);
        ontologyFactoryMap.put(name, ontology);
      }
    }
    ontologyFactoryList = null;
    
    // exclude categories
    for (MacroDeclaration macro : macroList) {
      if (macro.include(_projectId)) {
        String name = macro.getName();
        macro.excludeResources(_projectId);
        if (macro.isUsedByModel()) {
          if (modelMacroSet.contains(name))
            throw new WdkModelException("More than one model " + "macros '" + name + "' are defined");
          modelMacroSet.add(name);
        }
        if (macro.isUsedByJsp()) {
          if (jspMacroSet.contains(name))
            throw new WdkModelException("More than one jsp " + "macros '" + name + "' are defined");
          jspMacroSet.add(name);
        }
        if (macro.isUsedByPerl()) {
          if (perlMacroSet.contains(name))
            throw new WdkModelException("More than one perl " + "macros '" + name + "' are defined");
          perlMacroSet.add(name);
        }
      }
    }
    macroList = null;

    if (stepAnalysisPlugins != null) {
      stepAnalysisPlugins.excludeResources(_projectId);
    }
  }

  /**
   * this method has be to called after the excluding, but before resolving.
   */
  private void createInternalSets() throws WdkModelException {
    // create a param set to hold all internal params, that is, the params
    // created at run-time.
    boolean hasSet = false;
    for (ParamSet paramSet : paramSets.values()) {
      if (paramSet.getName().equals(Utilities.INTERNAL_PARAM_SET)) {
        hasSet = true;
        break;
      }
    }
    if (!hasSet) {
      ParamSet internalParamSet = new ParamSet();
      internalParamSet.setName(Utilities.INTERNAL_PARAM_SET);
      addSet(internalParamSet, paramSets);
      internalParamSet.excludeResources(_projectId);
    }

    // create a query set to hold all internal queries, that is, the queries
    // created at run-time.
    hasSet = false;
    for (QuerySet querySet : querySets.values()) {
      if (querySet.getName().equals(Utilities.INTERNAL_QUERY_SET)) {
        hasSet = true;
        break;
      }
    }
    if (!hasSet) {
      QuerySet internalQuerySet = new QuerySet();
      internalQuerySet.setName(Utilities.INTERNAL_QUERY_SET);
      internalQuerySet.setDoNotTest(true);
      addQuerySet(internalQuerySet);
      internalQuerySet.excludeResources(_projectId);
    }

    // create a query set to hold all internal questions, that is, the
    // questions created at run-time.
    hasSet = false;
    for (QuestionSet questionSet : questionSets.values()) {
      if (questionSet.getName().equals(Utilities.INTERNAL_QUESTION_SET)) {
        hasSet = true;
        break;
      }
    }
    if (!hasSet) {
      QuestionSet internalQuestionSet = new QuestionSet();
      internalQuestionSet.setInternal(true);
      internalQuestionSet.setName(Utilities.INTERNAL_QUESTION_SET);
      internalQuestionSet.setDoNotTest(true);
      addQuestionSet(internalQuestionSet);
      internalQuestionSet.excludeResources(_projectId);
    }
  }

  private void createBooleanQuestions() throws WdkModelException {
    for (RecordClassSet recordClassSet : getAllRecordClassSets()) {
      for (RecordClass recordClass : recordClassSet.getRecordClasses()) {
        getBooleanQuestion(recordClass);
      }
    }
  }

  @Override
  public String toString() {
    String userDatasetStoreStr = getUserDatasetStore() == null? "" : getUserDatasetStore().toString() + "NL";
    return new StringBuilder("WdkModel: ").append("projectId='").append(_projectId).append("'").append(NL).append(
        "displayName='").append(displayName).append("'").append(NL).append("introduction='").append(
        _introduction).append("'").append(NL).append(NL).append(userDatasetStoreStr).append(uiConfig.toString()).append(
        showSet("Param", paramSets)).append(showSet("Query", querySets)).append(
        showSet("RecordClass", recordClassSets)).append(showSet("XmlRecordClass", xmlRecordClassSets)).append(
        showSet("Question", questionSets)).append(showSet("XmlQuestion", xmlQuestionSets)).toString();
  }

  protected String showSet(String setType, Map<String, ? extends ModelSetI<? extends WdkModelBase>> setMap) {
    StringBuilder buf = new StringBuilder(NL).append(
        "ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo").append(NL).append(
        "ooooooooooooooooooooooooooooo ").append(setType).append(" Sets oooooooooooooooooooooooooo").append(
        NL).append("ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo").append(NL).append(
        NL);
    for (ModelSetI<? extends WdkModelBase> set : setMap.values()) {
      buf.append("=========================== ").append(set.getName()).append(
          " ===============================").append(NL).append(NL).append(set).append(NL);
    }
    return buf.append(NL).toString();
  }

  public void addQuestionSet(QuestionSet questionSet) throws WdkModelException {
    if (questionSetList != null)
      questionSetList.add(questionSet);
    else
      addSet(questionSet, questionSets);
  }

  public void addRecordClassSet(RecordClassSet recordClassSet) throws WdkModelException {
    if (recordClassSetList != null)
      recordClassSetList.add(recordClassSet);
    else
      addSet(recordClassSet, recordClassSets);
  }

  public void addQuerySet(QuerySet querySet) throws WdkModelException {
    if (querySetList != null)
      querySetList.add(querySet);
    else
      addSet(querySet, querySets);
  }

  public void addParamSet(ParamSet paramSet) throws WdkModelException {
    if (paramSetList != null)
      paramSetList.add(paramSet);
    else
      addSet(paramSet, paramSets);
  }

  public void addGroupSet(GroupSet groupSet) throws WdkModelException {
    if (groupSetList != null)
      groupSetList.add(groupSet);
    else
      addSet(groupSet, groupSets);
  }

  public void addFilterSet(FilterSet filterSet) throws WdkModelException {
    if (filterSetList != null)
      filterSetList.add(filterSet);
    else
      addSet(filterSet, filterSets);
  }

  public void addXmlQuestionSet(XmlQuestionSet questionSet) throws WdkModelException {
    if (xmlQuestionSetList != null)
      xmlQuestionSetList.add(questionSet);
    else
      addSet(questionSet, xmlQuestionSets);
  }

  public void addXmlRecordClassSet(XmlRecordClassSet recordClassSet) throws WdkModelException {
    if (xmlRecordClassSetList != null)
      xmlRecordClassSetList.add(recordClassSet);
    else
      addSet(recordClassSet, xmlRecordClassSets);
  }

  // =========================================================================
  // Xml data source related methods
  // =========================================================================

  public XmlQuestionSet[] getXmlQuestionSets() {
    XmlQuestionSet[] qsets = new XmlQuestionSet[xmlQuestionSets.size()];
    xmlQuestionSets.values().toArray(qsets);
    return qsets;
  }

  public XmlQuestionSet getXmlQuestionSet(String setName) throws WdkModelException {
    XmlQuestionSet qset = xmlQuestionSets.get(setName);
    if (qset == null)
      throw new WdkModelException("WDK Model " + _projectId +
          " does not contain an Xml Question set with name " + setName);
    return qset;
  }

  public XmlRecordClassSet[] getXmlRecordClassSets() {
    XmlRecordClassSet[] rcsets = new XmlRecordClassSet[xmlRecordClassSets.size()];
    xmlRecordClassSets.values().toArray(rcsets);
    return rcsets;
  }

  public XmlRecordClassSet getXmlRecordClassSet(String setName) throws WdkModelException {
    XmlRecordClassSet rcset = xmlRecordClassSets.get(setName);
    if (rcset == null)
      throw new WdkModelException("WDK Model " + _projectId +
          " does not contain an Xml Record Class set with name " + setName);
    return rcset;
  }

  public void setXmlSchema(URL xmlSchemaURL) {
    this.xmlSchemaURL = xmlSchemaURL;
  }

  public URL getXmlSchemaURL() {
    return xmlSchemaURL;
  }

  public void setXmlDataDir(File path) {
    this.xmlDataDir = path;
  }

  public File getXmlDataDir() {
    return xmlDataDir;
  }
  
  public UserDatasetStore getUserDatasetStore() {
    return userDatasetStore;
  }

  public DatasetFactory getDatasetFactory() {
    return datasetFactory;
  }

  public String getProjectId() {
    return _projectId;
  }

  /**
   * This method is supposed to be called by the digester
   * 
   * @param propertyList
   */
  public void addDefaultPropertyList(PropertyList propertyList) {
    this.defaultPropertyLists.add(propertyList);
  }

  /**
   * if the property list of the given name doesn't exist, an empty string array will be returned.
   * 
   * @param propertyListName
   * @return
   */
  public String[] getDefaultPropertyList(String propertyListName) {
    if (!defaultPropertyListMap.containsKey(propertyListName))
      return new String[0];
    return defaultPropertyListMap.get(propertyListName);
  }

  public Map<String, String[]> getDefaultPropertyLists() {
    Map<String, String[]> propLists = new LinkedHashMap<String, String[]>();
    for (String plName : defaultPropertyListMap.keySet()) {
      String[] values = defaultPropertyListMap.get(plName);
      propLists.put(plName, Arrays.copyOf(values, values.length));
    }
    return propLists;
  }

  public void addCategory(SearchCategory category) {
    this.categoryList.add(category);
  }

  public Map<String, SearchCategory> getCategories() {
    return getCategories(null);
  }

  public Map<String, SearchCategory> getCategories(String usedBy) {
    return getCategories(usedBy, false);
  }

  public Map<String, SearchCategory> getCategories(String usedBy, boolean strict) {
    if (eupathCategoriesFactory != null) {
      return eupathCategoriesFactory.getCategories(usedBy);
    }
    Map<String, SearchCategory> categories = new LinkedHashMap<>();
    for (String name : categoryMap.keySet()) {
      SearchCategory category = categoryMap.get(name);
      if (category.isUsedBy(usedBy, strict))
        categories.put(name, category);
    }
    return categories;
  }

  public Map<String, SearchCategory> getRootCategories(String usedBy) {
    if (eupathCategoriesFactory != null) {
      return eupathCategoriesFactory.getRootCategories(usedBy);
    }
    
    Map<String, SearchCategory> roots = new LinkedHashMap<String, SearchCategory>();
    for (SearchCategory root : rootCategoryMap.values()) {
      String cusedBy = root.getUsedBy();
      if (root.isUsedBy(cusedBy))
        roots.put(root.getName(), root);
    }
    return roots;
  }

  public void addOntology(OntologyFactoryImpl ontologyFactory) {
    this.ontologyFactoryList.add(ontologyFactory);
  }

  public Set<String> getOntologyNames() {
    return Collections.unmodifiableSet(ontologyFactoryMap.keySet());
  }

  private OntologyFactory getOntologyFactory(String name) {
    return ontologyFactoryMap.get(name);
  }

  public Ontology getOntology(String name) throws WdkModelException {
    OntologyFactory factory = getOntologyFactory(name);
    if (factory == null) return null;
    return factory.getOntology(this);
  }

  public void addMacroDeclaration(MacroDeclaration macro) {
    macroList.add(macro);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#finalize()
   */
  @Override
  protected void finalize() throws Throwable {
    LOG.debug("Model unloaded.");
  }

  public String queryParamDisplayName(String paramName) {
    for (String paramSetName : paramSets.keySet()) {
      ParamSet paramSet = paramSets.get(paramSetName);
      for (Param param : paramSet.getParams()) {
        if (param.getName().equals(paramName))
          return param.getPrompt();
      }
    }
    return paramName;
  }

  public String getSecretKey() throws WdkModelException {
    try {
      if (secretKey == null) {
        // load secret key file & read contents
        String secretKeyFileLoc = _modelConfig.getSecretKeyFile();
        if (secretKeyFileLoc == null)
          return null;

        File file = new File(secretKeyFileLoc);
        if (!file.exists())
          return null;

        InputStream fis = new FileInputStream(secretKeyFileLoc);
        StringBuilder contents = new StringBuilder();
        int chr;
        while ((chr = fis.read()) != -1) {
          contents.append((char) chr);
        }
        fis.close();
        this.secretKey = UserFactory.md5(contents.toString());
      }
      return secretKey;
    }
    catch (IOException e) {
      throw new WdkModelException("Unable to retrieve secret key from file.", e);
    }
  }

  public boolean getUseWeights() {
    return _modelConfig.getUseWeights();
  }

  public User getSystemUser() throws WdkModelException {
    if (systemUser == null) {
      try {
        // ideally would synchronize on systemUser but cannot sync on null so use lock
        systemUserLock.lock();
        if (systemUser == null) {
          systemUser = userFactory.createSystemUser();
        }
      }
      finally {
        systemUserLock.unlock();
      }
    }
    return systemUser;
  }

  public BasketFactory getBasketFactory() {
    return basketFactory;
  }

  public FavoriteFactory getFavoriteFactory() {
    return favoriteFactory;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  /**
   * @return the queryMonitor
   */
  public QueryMonitor getQueryMonitor() {
    return _modelConfig.getQueryMonitor();
  }

  /**
   * @return the buildNumber
   */
  public String getBuildNumber() {
    return buildNumber;
  }

  /**
   * @param buildNumber
   *          the buildNumber to set
   */
  public void setBuildNumber(String buildNumber) {
    this.buildNumber = buildNumber;
  }

  public String getGusHome() {
    return _gusHome;
  }

  public void setGusHome(String gusHome) {
    _gusHome = gusHome;
  }

  public void setUIConfig(UIConfig uiConfig) {
    this.uiConfig = uiConfig;
  }

  public UIConfig getUIConfig() {
    return uiConfig;
  }

  public StepAnalysisPlugins getStepAnalysisPlugins() {
    return stepAnalysisPlugins;
  }

  public void setStepAnalysisPlugins(StepAnalysisPlugins stepAnalysisPlugins) {
    this.stepAnalysisPlugins = stepAnalysisPlugins;
  }

  public ExampleStratsAuthor getExampleStratsAuthor() {
    return exampleStratsAuthor;
  }

  public void setExampleStratsAuthor(ExampleStratsAuthor exampleStratsAuthor) {
    this.exampleStratsAuthor = exampleStratsAuthor;
  }

  @Override
  public Connection getConnection(String key) throws WdkModelException, SQLException {
    switch (key) {
      case DB_INSTANCE_APP:
        return appDb.getDataSource().getConnection();
      case DB_INSTANCE_USER:
        return userDb.getDataSource().getConnection();
      default: // unknown
        throw new WdkModelException("Invalid DB Connection key.");
    }
  }

  public void logStepAnalysisPlugins() {
    StringBuilder sb = new StringBuilder().append("*******************************************\n").append(
        "Included Step Analysis Plugin Configuration:\n").append(stepAnalysisPlugins.toString()).append(
        "*******************************************\n").append("Step Analysis Plugins per Question:\n");
    for (QuestionSet questionSet : getQuestionSets().values()) {
      for (Question question : questionSet.getQuestions()) {
        Map<String, StepAnalysis> sas = question.getStepAnalyses();
        if (!sas.isEmpty()) {
          sb.append("Plugins for Question:" + question.getFullName() + "\n");
          for (StepAnalysis sa : sas.values()) {
            sb.append(sa);
          }
        }
      }
    }
    LOG.info(sb.toString());
  }

  public String getDependencyTree() throws WdkModelException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    writer.println("<wdkModel>");

    // print questions
    String[] setNames = questionSets.keySet().toArray(new String[0]);
    Arrays.sort(setNames);
    for (String setName : setNames) {
      writer.println(INDENT + "<questionSet name=\"" + setName + "\">");
      Map<String, Question> questions = questionSets.get(setName).getQuestionMap();
      String[] questionNames = questions.keySet().toArray(new String[0]);
      for (String questionName : questionNames) {
        questions.get(questionName).printDependency(writer, INDENT + INDENT);
      }
      writer.println(INDENT + "</questionSet>");
    }

    // print record classes
    setNames = recordClassSets.keySet().toArray(new String[0]);
    Arrays.sort(setNames);
    for (String setName : setNames) {
      writer.println(INDENT + "<recordClassSet name=\"" + setName + "\">");
      Map<String, RecordClass> recordClasses = recordClassSets.get(setName).getRecordClassMap();
      String[] rcNames = recordClasses.keySet().toArray(new String[0]);
      for (String rcName : rcNames) {
        recordClasses.get(rcName).printDependency(writer, INDENT + INDENT);
      }
      writer.println(INDENT + "</recordClassSet>");
    }

    writer.println("</wdkModel>");
    return stringWriter.toString();
  }

  public void registerRecordClassUrlSegment(String urlSegment, String rcFullName) throws WdkModelException {
    if (_recordClassUrlSegmentMap.containsKey(urlSegment) &&
        !_recordClassUrlSegmentMap.get(urlSegment).equals(rcFullName)) { // protects from duplicate identical calls
      throw new WdkModelException("Duplicate RecordClass URL segment specified [" + urlSegment + "]");
    }
    _recordClassUrlSegmentMap.put(urlSegment, rcFullName);
  }

  public RecordClass getRecordClassByUrlSegment(String urlSegment) throws WdkModelException {
    String recordClassFullName = _recordClassUrlSegmentMap.get(urlSegment);
    return (recordClassFullName == null ? null : getRecordClass(recordClassFullName));
  }

  public void registerQuestionUrlSegment(String urlSegment, String questionFullName) throws WdkModelException {
    if (_questionUrlSegmentMap.containsKey(urlSegment) &&
        !_questionUrlSegmentMap.get(urlSegment).equals(questionFullName)) { // protects from duplicate identical calls
      throw new WdkModelException("Duplicate Question URL segment specified [" + urlSegment +
          "]. You may need to specify a custom URL segment if you use have " +
          "two questions with the same name in different Question Sets.");
    }
    _questionUrlSegmentMap.put(urlSegment, questionFullName);
  }

  public Question getQuestionByUrlSegment(String urlSegment) throws WdkModelException {
    String questionFullName = _questionUrlSegmentMap.get(urlSegment);
    return (questionFullName == null ? null : getQuestion(questionFullName));
  }

  public static AutoCloseableList<WdkModel> loadMultipleModels(String gusHome, String[] projects) throws WdkModelException {
    AutoCloseableList<WdkModel> models = new AutoCloseableList<>();
    try {
      for (String projectId : projects) {
        models.add(WdkModel.construct(projectId, gusHome));
      }
      return models;
    }
    catch (Exception e) {
      models.close();
      throw e;
    }
  }
}
