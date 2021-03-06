package org.gusdb.wdk.model.user.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.EncryptionUtil;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.fgputil.validation.ValidObjectFactory.RunnableObj;
import org.gusdb.fgputil.validation.ValidationLevel;
import org.gusdb.wdk.model.WdkException;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkRuntimeException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.analysis.StepAnalysis;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.answer.factory.AnswerValueFactory;
import org.gusdb.wdk.model.query.param.AbstractEnumParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.spec.ParameterContainerInstanceSpecBuilder.FillStrategy;
import org.gusdb.wdk.model.query.spec.StepAnalysisFormSpec;
import org.gusdb.wdk.model.query.spec.StepAnalysisFormSpecBuilder;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.user.Step;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Encapsulation of values associated with a particular instance of a step
 * analysis plugin (as identified as a tab in the UI).  Instances have their own
 * IDs and params, but may share results if they are similar enough.  This class
 * is responsible for generating the JSON sent to the client and the instance hash
 * used to look up results, and contains the current state/status of the
 * instance (as influenced by whether it's been run before, has params, and has
 * results).
 *
 * Notes:
 *   State tells the UI whether to show empty results, "Invalid due to revise",
 *     or normal request of results (which may show out-of-date, error, etc.)
 *   HasParams tells the UI whether to repopulate form params from stored values
 *
 * @author rdoherty
 */
public class StepAnalysisInstance {

  public static final Logger LOG = Logger.getLogger(StepAnalysisInstance.class);

  public static final String ANALYSIS_ID_KEY = "analysisId";

  public static enum JsonKey {

    // the following values define the hashable serialized instance
    analysisName,
    answerValueHash,
    formParams,

    // the following values are included with JSON returned to client
    analysisId,
    stepId,
    displayName,
    shortDescription,
    description,
    status,
    hasParams,
    invalidStepReason,
    userNotes
  }

  private WdkModel _wdkModel;
  private long _analysisId;
  private String _editableDisplayName;
  private String _userNotes;
  private Step _step;
  private String _answerValueHash;
  private StepAnalysis _stepAnalysis;
  private StepAnalysisState _state;
  private boolean _hasParams;
  private String _invalidStepReason;
  private ExecutionStatus _status;
  private Map<String, String[]> _formParams;

  private StepAnalysisInstance() { }

  /**
   * Creates a step analysis instance.   Does not yet have an analysis id and
   * will receive one when it is written to the database.
   *
   * This is package scope, and should be called only by the factory.
   *
   * @param stepAnalysis descriptor of the step analysis that will be invoked
   * @param step step referred to by this analysis
   * @param answerValueChecksum the checksum provided by answerValue.getChecksum()
   * @throws WdkModelException if something goes wrong during creation
   * @throws WdkUserException if the passed values do not refer to real objects
   */
  static StepAnalysisInstance createNewInstance(Step step, StepAnalysis stepAnalysis, String answerValueChecksum) throws WdkModelException {

    StepAnalysisInstance ctx = new StepAnalysisInstance();
    ctx._analysisId = -1;
    ctx._wdkModel = step.getUser().getWdkModel();
    ctx._step = step;
    ctx._answerValueHash = answerValueChecksum;

    ctx._stepAnalysis = stepAnalysis;

    if (ctx._stepAnalysis == null) throw new WdkModelException ("Null stepAnalysis");

    ctx._editableDisplayName = ctx._stepAnalysis.getDisplayName();
    ctx._formParams = new HashMap<>();
    ctx._state = StepAnalysisState.NO_RESULTS;
    ctx._hasParams = false;
    ctx._invalidStepReason = null;
    ctx._status = ExecutionStatus.CREATED;

    return ctx;
  }

  private static String getAnswerValueHash(Step step) throws WdkModelException {
    return !step.isRunnable() ? "" :
      AnswerValueFactory.makeAnswer(step.getRunnable().getLeft()).getChecksum();
  }

  public static StepAnalysisInstance createFromId(long analysisId, StepAnalysisFactory analysisMgr)
      throws WdkUserException, WdkModelException {
    return analysisMgr.getSavedAnalysisInstance(analysisId);
  }

  public static StepAnalysisInstance createFromStoredData(WdkModel wdkModel,
      long analysisId, long stepId, StepAnalysisState state, boolean hasParams, String invalidStepReason,
      String displayName, String userNotes, String serializedInstance) throws WdkModelException, DeprecatedAnalysisException {
    try {
      StepAnalysisInstance ctx = new StepAnalysisInstance();
      ctx._wdkModel = wdkModel;
      ctx._analysisId = analysisId;
      ctx._editableDisplayName = displayName;
      ctx._userNotes = userNotes;
      ctx._state = state;
      ctx._hasParams = hasParams;
      ctx._invalidStepReason = invalidStepReason;
      ctx._status = ExecutionStatus.UNKNOWN;

      LOG.debug("Got the following serialized instance from the DB: " + serializedInstance);

      // deserialize hashable instance values
      JSONObject json = new JSONObject(serializedInstance);
      ctx._step = loadStep(ctx._wdkModel, stepId, new WdkModelException("Unable " +
          "to find step (ID=" + stepId + ") defined in step analysis instance (ID=" + analysisId + ")"));
      ctx._answerValueHash = getAnswerValueHash(ctx._step);
      Question question = ctx._step.getAnswerSpec().getQuestion();
      ctx._stepAnalysis = question.getStepAnalysis(json.getString(JsonKey.analysisName.name()));

      ctx._formParams = new LinkedHashMap<>();
      JSONObject formObj = json.getJSONObject(JsonKey.formParams.name());
      LOG.debug("Retrieved the following params JSON from the DB: " + formObj);

      for (String key : JsonUtil.getKeys(formObj)) {
        JSONArray array = formObj.getJSONArray(key);
        String[] values = new String[array.length()];
        for (int i=0; i < array.length(); i++) {
          values[i] = array.getString(i);
        }
        ctx._formParams.put(key, values);
      }

      return ctx;
    }
    catch (WdkUserException e) {
      throw new DeprecatedAnalysisException("Illegal step analysis plugin " +
          "name for analysis with ID: " + analysisId, e);
    }
    catch (WdkModelException e) {
      throw new DeprecatedAnalysisException("Unable to construct instance " +
          "from analysis with ID: " + analysisId, e);
    }
    catch (JSONException e) {
      throw new WdkModelException("Unable to deserialize instance.", e);
    }
  }

  public static StepAnalysisInstance createCopy(StepAnalysisInstance oldInstance) {
    StepAnalysisInstance ctx = new StepAnalysisInstance();
    ctx._wdkModel = oldInstance._wdkModel;
    ctx._analysisId = oldInstance._analysisId;
    ctx._editableDisplayName = oldInstance._editableDisplayName;
    ctx._userNotes = oldInstance._userNotes;
    ctx._step = oldInstance._step;
    ctx._answerValueHash = oldInstance._answerValueHash;
    ctx._stepAnalysis = oldInstance._stepAnalysis;
    // deep copy params
    ctx._formParams = getDuplicateMap(oldInstance._formParams);
    ctx._state = oldInstance._state;
    ctx._hasParams = oldInstance._hasParams;
    ctx._invalidStepReason = oldInstance._invalidStepReason;
    ctx._status = oldInstance._status;
    return ctx;
  }

  private static <T extends WdkException> Step loadStep(WdkModel wdkModel, long stepId,
      T wdkUserException) throws T {
    try {
      return wdkModel.getStepFactory().getStepByValidId(stepId, ValidationLevel.RUNNABLE);
    }
    catch (WdkModelException e) {
      throw wdkUserException;
    }
  }

  private static Map<String, String[]> getDuplicateMap(Map<String, String[]> formParams) {
    Map<String, String[]> newParamMap = new HashMap<>(formParams);
    for (String key : newParamMap.keySet()) {
      String[] old = newParamMap.get(key);
      if (old != null) {
        newParamMap.put(key, Arrays.copyOf(old, old.length));
      }
    }
    return newParamMap;
  }

  /**
   * Returns JSON of the following spec (for generating hash):
   * {
   *   analysisName: string
   *   answerValueHash: string
   *   params: key-value object of params
   */
  public String serializeInstance() {
    try {
      JSONObject jsonForDigest = new JSONObject()
          .put(JsonKey.analysisName.name(), _stepAnalysis.getName())
          .put(JsonKey.answerValueHash.name(), _answerValueHash)
          .put(JsonKey.formParams.name(), getRawParamsJson());

      LOG.debug("Created the following digest JSON: " + jsonForDigest);
      return JsonUtil.serialize(jsonForDigest);
    }
    catch (JSONException e) {
      throw new WdkRuntimeException("Unable to serialize instance.", e);
    }
  }

  private JSONObject getRawParamsJson() {
    // Sort param names so JSON values produce identical hashes
    List<String> sortedParamNames = new ArrayList<>(_formParams.keySet());
    Collections.sort(sortedParamNames);
    JSONObject params = new JSONObject();
    for (String paramName : sortedParamNames) {
      // Sort param values so JSON values produce identical hashes
      List<String> paramValues = Arrays.asList(_formParams.get(paramName));
      Collections.sort(paramValues);
      for (String value : paramValues) {
        params.append(paramName, value);
      }
    }
    return params;
  }

  public String createHash() {
    return createHashFromString(serializeInstance());
  }

  public static String createHashFromString(String serializedInstance) {
    try {
      return EncryptionUtil.encrypt(serializedInstance);
    }
    catch (Exception e) {
      throw new WdkRuntimeException("Unable to generate checksum from serialized instance.", e);
    }
  }

  public long getAnalysisId() {
    return _analysisId;
  }

  public void setAnalysisId(long analysisId) {
    _analysisId = analysisId;
  }

  public String getDisplayName() {
    return _editableDisplayName;
  }

  public void setDisplayName(String displayName) {
    _editableDisplayName = displayName;
  }

public String getUserNotes() {
    return _userNotes;
  }

  public void setUserNotes(String userNotes) {
    _userNotes = userNotes;
  }

  public Step getStep() {
    return _step;
  }

  public void setStep(Step step) {
    _step = step;
  }

  public StepAnalysis getStepAnalysis() {
    return _stepAnalysis;
  }

  public Map<String, String[]> getFormParams() {
    return _formParams;
  }

  public ExecutionStatus getStatus() {
    return _status;
  }

  public void setStatus(ExecutionStatus status) {
    _status = status;
  }

  public StepAnalysisState getState() {
    return _state;
  }

  public void setState(StepAnalysisState state) {
    _state = state;
  }

  public boolean hasParams() {
    return _hasParams;
  }

  public void setHasParams(boolean hasParams) {
    _hasParams = hasParams;
  }

  public boolean getIsValidStep() {
    return (_invalidStepReason == null || _invalidStepReason.isEmpty());
  }

  public String getInvalidStepReason() {
    return _invalidStepReason;
  }

  public void setIsValidStep(boolean isValidStep) {
    setIsValidStep(isValidStep, null);
  }

  public void setIsValidStep(boolean isValidStep, String invalidReason) {
    // valid steps have no invalid reasons; set to null
    _invalidStepReason = (isValidStep ? null :
      // invalid steps must give a reason or one will be provided
      (invalidReason == null || invalidReason.isEmpty()) ?
          "Unable to determine." : invalidReason);
  }

  /**
   * Generates and returns a salted access token.  If user can present
   * this token, they will have access to restricted properties of
   * this particular analysis.
   *
   * @return salted access token
   * @throws WdkModelException if unable to read WDK model's secret key file
   */
  public String getAccessToken() throws WdkModelException {
    return EncryptionUtil.encrypt("__" + _analysisId + _step.getStepId() + _wdkModel.getModelConfig().getSecretKey(), true);
  }

  public AnswerValue getAnswerValue() throws WdkUserException, WdkModelException {
    if (!getStep().isRunnable()) {
      throw new WdkUserException("Cannot execute an analysis on an unrunnable step.");
    }
    return AnswerValueFactory.makeAnswer(getStep().getRunnable().getLeft());
  }

  public String getAnswerValueHash() {
    return _answerValueHash;
  }

  public StepAnalysisFormSpec getFormSpec(ValidationLevel validationLevel, FillStrategy fillStrategy) throws WdkModelException {
    // FIXME: This is a hack to transform the current DB format for params into
    //   our desired service API format.  The difference is that the DB currently
    //   stores params as a Map<String,String[]>, conforming to the previous
    //   Servlet form param map type.  To convert to a form spec, we need the
    //   param values in a Map<String,String>, where the values are stable values
    //   of WDK params.  See transformation below.

    StepAnalysisFormSpecBuilder builder = StepAnalysisFormSpec.builder();
    for (Param param : _stepAnalysis.getParams()) {
      String[] values = _formParams.get(param.getName());
      // handle empty case (param value missing)
      if (values == null || values.length == 0) continue; // don't add any value; may fill later
      // convert from array to old-style single "stable" value
      if (values.length > 1) values[0] = FormatUtil.join(values, ",");
      // get standardized value and add to builder
      builder.put(param.getName(), param.getStandardizedStableValue(values[0]));
    }
    RunnableObj<Step> runnableStep = _step.getRunnable().getOrThrow(step ->
        new WdkModelException("Request made to produce step analysis form spec on analysis owned " +
            "by an unrunnable step. " + step.getValidationBundle().toString(2)));
    return builder.buildValidated(runnableStep, _stepAnalysis, validationLevel, fillStrategy);
  }

  public void setFormSpec(RunnableObj<StepAnalysisFormSpec> validFormSpec) {
    // FIXME: Same hack for the same reason as getFormSpec() above, opposite direction
    _formParams.clear();
    LOG.info("Assigning param map of arrays from valid form spec");
    for (Param param : _stepAnalysis.getParams()) {
      // get the standardized stable value from the spec
      String stableValue = validFormSpec.get().get(param.getName());
      String[] arrayValue = (param instanceof AbstractEnumParam
          ? AbstractEnumParam.convertToTerms(stableValue).toArray(new String[0])
          : new String[]{ stableValue });
      LOG.info("Adding name: " + param.getName() + ", value: " + FormatUtil.join(arrayValue, ","));
      _formParams.put(param.getName(), arrayValue);
    }
  }

}
