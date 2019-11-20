package org.gusdb.wdk.service.service.user;

import static org.gusdb.wdk.service.service.user.StepService.STEP_ID_PATH_PARAM;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.fgputil.validation.ValidObjectFactory.RunnableObj;
import org.gusdb.fgputil.validation.ValidationBundle;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.analysis.StepAnalysis;
import org.gusdb.wdk.model.answer.factory.AnswerValueFactory;
import org.gusdb.wdk.model.query.param.AbstractEnumParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.user.Step;
import org.gusdb.wdk.model.user.analysis.StepAnalysisFactory;
import org.gusdb.wdk.model.user.analysis.StepAnalysisInstance;
import org.gusdb.wdk.service.annotation.PATCH;
import org.gusdb.wdk.service.formatter.StepAnalysisFormatter;
import org.gusdb.wdk.service.request.exception.DataValidationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides endpoints related to step analysis instances.  All endpoints are
 * relative to /users/{id}/steps/{id} (i.e. all pertain to a particular step).
 * They are:
 * 
 * GET    /analyses                      Returns basic info about analysis instances applied on this step (for population of tabs)
 * POST   /analyses                      Create a new analysis instance (input data contains type and param values)
 * GET    /analyses/{id}                 Returns information about an analysis instance (type, display name, status, param values, etc.)
 * PATCH  /analyses/{id}                 Update persisted analysis instance data (display name, param values)
 * DELETE /analyses/{id}                 Deletes an analysis instance
 * POST   /analyses/{id}/result          Kicks off an analysis run if not already running
 * GET    /analyses/{id}/result          Returns analysis run results if available
 * GET    /analyses/{id}/result/status   Returns analysis run results if available
 * GET    /analyses/{id}/resources?path  Returns a file resource generated by this analysis instance
 * GET    /analyses/{id}/properties      Returns arbitrary properties attached to this analysis instance
 * PUT    /analyses/{id}/properties      Sets arbitrary properties attached to this analysis instance
 * 
 * See also: StepAnalysisFormService
 * 
 * @author eharper
 */
public class StepAnalysisInstanceService extends UserService implements StepAnalysisLookupMixin {

  // endpoints to handle analysis instances for a given step
  private static final String ANALYSES_PATH = StepService.NAMED_STEP_PATH + "/analyses";
  private static final String ANALYSIS_ID_PATH_PARAM = "analysisId";
  private static final String NAMED_ANALYSIS_PATH = ANALYSES_PATH + "/{" + ANALYSIS_ID_PATH_PARAM + "}";

  // sub-endpoints for an analysis instance
  private static final String NAMED_ANALYSIS_RESULT_PATH = NAMED_ANALYSIS_PATH + "/result";
  private static final String NAMED_ANALYSIS_RESULT_STATUS_PATH = NAMED_ANALYSIS_RESULT_PATH + "/status";
  private static final String NAMED_ANALYSIS_RESOURCES_PATH = NAMED_ANALYSIS_PATH + "/resources";
  private static final String NAMED_ANALYSIS_PROPERTIES_PATH = NAMED_ANALYSIS_PATH + "/properties";

  private static final String ACCESS_TOKEN_QUERY_PARAM = "accessToken";

  private static final String ANALYSIS_PARAMS_KEY = StepAnalysisInstance.JsonKey.formParams.name();
  private static final String ANALYSIS_NAME_KEY = StepAnalysisInstance.JsonKey.analysisName.name();
  private static final String ANALYSIS_DISPLAY_NAME_KEY = StepAnalysisInstance.JsonKey.displayName.name();
  private static final String STATUS_KEY = "status";
  private static final String CONTEXT_HASH_KEY = "contextHash";
  private static final String ACCESS_TOKEN_KEY = "accessToken";
  private static final String DOWNLOAD_URL_KEY = "downloadUrl";
  private static final String PROPERTIES_URL_KEY = "propertiesUrl";

  private final long _stepId;

  protected StepAnalysisInstanceService(
      @PathParam(USER_ID_PATH_PARAM) String uid,
      @PathParam(STEP_ID_PATH_PARAM) long stepId) {
    super(uid);
    _stepId = stepId;
  }

  @Override
  public long getStepId() {
    return _stepId;
  }

  /**
   * Create a new step analysis
   *
   * @param body input JSON string
   * @return Details of the newly created step analysis instance as JSON
   */
  @POST
  @Path(ANALYSES_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public String createStepAnalysis(JSONObject json) throws WdkModelException {
    try {
      RunnableObj<Step> step = getRunnableStepForCurrentUser(_stepId);
      String analysisName = json.getString(ANALYSIS_NAME_KEY);
      String answerValueChecksum = AnswerValueFactory.makeAnswer(step).getChecksum();
      StepAnalysis stepAnalysis = getStepAnalysisFromQuestion(step.get().getAnswerSpec().getQuestion(), analysisName);
      StepAnalysisInstance stepAnalysisInstance = getStepAnalysisInstance(step, stepAnalysis, answerValueChecksum);

      return StepAnalysisFormatter.getStepAnalysisInstanceJson(stepAnalysisInstance).toString();
    }
    catch (JSONException | DataValidationException e) {
      throw new BadRequestException(e);
    }
  }

  /**
   * List of applied step analysis instances
   *
   * @return JSON response containing an array of basic analysis instance details
   * @throws DataValidationException
   */
  @GET
  @Path(ANALYSES_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public JSONArray getStepAnalysisInstanceList() throws WdkModelException, DataValidationException {
    getUserBundle(Access.PRIVATE); // make sure session user matches target user
    final Map<Long, StepAnalysisInstance> analyses = getWdkModel()
        .getStepAnalysisFactory()
        .getAppliedAnalyses(getRunnableStepForCurrentUser(_stepId).get());
    return StepAnalysisFormatter.getStepAnalysisInstancesJson(analyses);
  }

  @GET
  @Path(NAMED_ANALYSIS_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public JSONObject getStepAnalysisInstance(
      @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
      @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken) throws WdkModelException {
    return StepAnalysisFormatter.getStepAnalysisInstanceJson(getAnalysis(analysisId, accessToken));
  }

  //  TODO: Why is this so slow?
  @DELETE
  @Path(NAMED_ANALYSIS_PATH)
  public void deleteStepAnalysisInstance(
      @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
      @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken) throws WdkModelException {
    getWdkModel().getStepAnalysisFactory().deleteAnalysis(getAnalysis(analysisId, accessToken));
  }

  @PATCH
  @Path(NAMED_ANALYSIS_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateStepAnalysisInstance(
      @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
      @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken,
      String body) throws WdkModelException, WdkUserException {
    final ObjectMapper mapper = new ObjectMapper();
    final StepAnalysisFactory factory = getWdkModel().getStepAnalysisFactory();
    final StepAnalysisInstance instance = getAnalysis(analysisId, accessToken);
    final JsonNode json;
    try {
      json = mapper.readTree(body);
      if (json.has(ANALYSIS_PARAMS_KEY)) {
        final Map<String, String> inputParams = mapper.readerFor(
            new TypeReference<Map<String, String>>() {}).readValue(json.get(ANALYSIS_PARAMS_KEY));

        instance.setFormParams(translateParamValues(instance.getStepAnalysis().getParamMap(), inputParams));

        ValidationBundle validation = factory.validateFormParams(instance);

        if(!validation.getStatus().isValid()) {
          throw new WdkUserException(validation.toString(2));
        }

        factory.setFormParams(instance);
      }
    }
    catch (IOException e) {
      throw new WdkModelException(e);
    }

    if (json.has(ANALYSIS_DISPLAY_NAME_KEY)) {
      instance.setDisplayName(json.get(ANALYSIS_DISPLAY_NAME_KEY).asText());
      factory.renameInstance(instance);
    }
  }

  private Map<String,String[]> translateParamValues(Map<String, Param> paramMap, Map<String, String> inputParams) {
    // FIXME: try to get to where this method is not needed.  It is very flawed.
    //   For more explanation see: StepAnalysisFormatter.getStepAnalysisInstanceJson()
    return Functions.getMapFromKeys(inputParams.keySet(),
        key -> paramMap.get(key) instanceof AbstractEnumParam
            ? inputParams.get(key).split(",")
            : new String[]{inputParams.get(key)});
  }

  @GET
  @Path(NAMED_ANALYSIS_RESULT_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStepAnalysisResult(
    @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
    @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken
  ) throws WdkModelException, WdkUserException {

    final StepAnalysisFactory fac = getWdkModel().getStepAnalysisFactory();
    final JSONObject value = fac.getAnalysisResult(getAnalysis(analysisId, accessToken))
      .getResultViewModelJson();

    if(value == null)
      return Response.noContent().build();

    // This should be moved upstream.
    StepAnalysisInstance inst = fac.getSavedAnalysisInstance(analysisId);
    String analysisUrl = getAnalysisUrl(inst);
    value.put(CONTEXT_HASH_KEY, inst.createHash())
        .put(ACCESS_TOKEN_KEY, inst.getAccessToken())
        .put(DOWNLOAD_URL_KEY, analysisUrl + "/resources")
        .put(PROPERTIES_URL_KEY, analysisUrl + "/properties");

    return Response.ok(value).build();
  }

  private String getAnalysisUrl(StepAnalysisInstance inst) {
    return String.format("%s/users/%d/steps/%d/analyses/%d",
        getServiceUri(), inst.getStep().getUser().getUserId(),
        inst.getStep().getStepId(), inst.getAnalysisId());
  }

  @POST
  @Path(NAMED_ANALYSIS_RESULT_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public Response runAnalysis(
    @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
    @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken
  ) throws WdkModelException {
    final StepAnalysisInstance instance = getAnalysis(analysisId, accessToken);

    getWdkModel().getStepAnalysisFactory().runAnalysis(instance);

    return Response.accepted()
        .entity(new JSONObject().put(STATUS_KEY, instance.getStatus().name()))
        .build();
  }

  @GET
  @Path(NAMED_ANALYSIS_RESULT_STATUS_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public JSONObject getStepAnalysisResultStatus(
    @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId
  ) throws WdkModelException {
    try {
      return new JSONObject().put(
        STATUS_KEY,
        getWdkModel().getStepAnalysisFactory()
          .getSavedAnalysisInstance(analysisId)
          .getStatus()
          .name()
      );
    } catch (WdkUserException e) {
      throw new NotFoundException(e);
    }
  }

  @GET
  @Path(NAMED_ANALYSIS_RESOURCES_PATH)
  public Response getStepAnalysisResource(
    @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
    @QueryParam("path") String path
  ) throws Exception {
    StepAnalysisFactory stepAnalysisFactory = getWdkModel().getStepAnalysisFactory();
    StepAnalysisInstance instance = StepAnalysisInstance.createFromId(
      analysisId,
      stepAnalysisFactory
    );
    java.nio.file.Path resourcePath = stepAnalysisFactory.getResourcePath(
      instance,
      path
    );

    File resourceFile = resourcePath.toFile();
    if (resourceFile.exists() && resourceFile.isFile() && resourceFile.canRead()) {
      InputStream resourceStream = new BufferedInputStream(new FileInputStream(resourceFile));
      return Response.ok(getStreamingOutput(resourceStream))
        .type(Files.probeContentType(resourcePath))
        .header(
          "Content-Disposition",
          ContentDisposition.type("attachment").fileName(
            resourceFile.getName()
          ).build()
        )
        .build();
    }

    throw new NotFoundException("Could not find resource " + path + " for step analysis " + analysisId);
  }

  @GET
  @Path(NAMED_ANALYSIS_PROPERTIES_PATH)
  @Produces(MediaType.TEXT_PLAIN)
  public Response getStepAnalysisProperties(
    @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
    @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken
  ) throws WdkModelException {
    StepAnalysisInstance instance = getAnalysis(analysisId, accessToken);
    InputStream propertiesStream = getWdkModel().getStepAnalysisFactory()
        .getProperties(instance);
    return Response.ok(getStreamingOutput(propertiesStream)).build();
  }

  // TODO: this should 404 if the analysis or step id are not found, presently it 500s
  @PUT
  @Path(NAMED_ANALYSIS_PROPERTIES_PATH)
  @Consumes(MediaType.TEXT_PLAIN)
  public void setStepAnalysisProperties(
      @PathParam(ANALYSIS_ID_PATH_PARAM) long analysisId,
      @QueryParam(ACCESS_TOKEN_QUERY_PARAM) String accessToken,
      InputStream body) throws WdkModelException {
    StepAnalysisInstance instance = getAnalysis(analysisId, accessToken);
    getWdkModel().getStepAnalysisFactory().setProperties(instance, body);
  }
}
