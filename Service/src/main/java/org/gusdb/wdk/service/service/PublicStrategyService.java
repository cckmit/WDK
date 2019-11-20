package org.gusdb.wdk.service.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.wdk.core.api.JsonKeys;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.user.InvalidStrategyStructureException;
import org.gusdb.wdk.model.user.Strategy;
import org.gusdb.wdk.model.user.Strategy.StrategyBuilder;
import org.gusdb.wdk.model.user.StrategyLoader.UnbuildableStrategyList;
import org.gusdb.wdk.service.formatter.StrategyFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Provides list of public strategies
 *
 * TODO: rename this class to StrategyListsService, after merge into trunk
 */
@Path("/strategy-lists/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicStrategyService extends AbstractWdkService {

  /**
   * Get a list of all valid public strategies
   * the isInvalid query param is undocumented, and for internal use only, allowing
   * developers to review invalid strategies
   */
  @GET
  public JSONArray getPublicStrategies(
      @QueryParam("userEmail") List<String> userEmails,
      @QueryParam("invalid") @DefaultValue("false") Boolean returnInvalid)
  throws JSONException, WdkModelException {
    Stream<Strategy> strategies = getWdkModel()
      .getStepFactory()
      .getPublicStrategies()
      .stream()
      .filter(returnInvalid ?
        Functions.not(Strategy::isValid) :
        Strategy::isValid);

    if (!userEmails.isEmpty())
      strategies = strategies.filter(strat -> userEmails.stream()
        .anyMatch(userEmail -> strat.getUser()
          .getEmail()
          .equals(userEmail)));

    return StrategyFormatter.getStrategiesJson(strategies.collect(Collectors.toList()));
  }

  /**
   * Get a list of the IDs of public strategies that cannot be built due to
   * exceptions occurring during building.
   * @throws WdkModelException 
   */
  @GET
  @Path("/errors")
  @Produces(MediaType.APPLICATION_JSON)
  public JSONObject getErroredPublicStrategies() throws WdkModelException {
    assertAdmin();
    TwoTuple<
      UnbuildableStrategyList<InvalidStrategyStructureException>,
      UnbuildableStrategyList<WdkModelException>
    > erroredStrats = getWdkModel()
      .getStepFactory()
      .getPublicStrategyErrors();
    return new JSONObject()
      .put("structuralErrors", formatErrors(erroredStrats.getFirst()))
      .put("buildErrors", formatErrors(erroredStrats.getSecond()));
  }

  private <T extends Exception> JSONArray formatErrors(UnbuildableStrategyList<T> list) {
    JSONArray arr = new JSONArray();
    for (TwoTuple<StrategyBuilder, T> erroredStrat : list) {
      StrategyBuilder strat = erroredStrat.getFirst();
      Exception e = erroredStrat.getSecond();
      arr.put(new JSONObject()
        .put(JsonKeys.STRATEGY_ID, strat.getStrategyId())
        .put(JsonKeys.USER_ID, strat.getUserId())
        .put(JsonKeys.EXCEPTION, new JSONObject()
          .put(JsonKeys.MESSAGE, e.getMessage())
          .put(JsonKeys.STACK_TRACE, FormatUtil.getStackTrace(e))));
    }
    return arr;
  }

}
