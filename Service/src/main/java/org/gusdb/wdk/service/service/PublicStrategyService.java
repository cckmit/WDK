package org.gusdb.wdk.service.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.gusdb.fgputil.functional.Functions;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.user.Strategy;
import org.gusdb.wdk.service.formatter.StrategyFormatter;
import org.json.JSONArray;
import org.json.JSONException;


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
      @QueryParam("invalid") Boolean returnInvalid)
  throws JSONException, WdkModelException {
    boolean showInvalid = false;
    if (returnInvalid != null ) showInvalid = returnInvalid;
    var strategies = getWdkModel()
      .getStepFactory()
      .getPublicStrategies()
      .stream()
      .filter(showInvalid ?
        Strategy::isValid :
        Functions.not(Strategy::isValid));

    if (!userEmails.isEmpty())
      strategies = strategies.filter(strat -> userEmails.stream()
        .anyMatch(userEmail -> strat.getUser()
          .getEmail()
          .equals(userEmail)));

    return StrategyFormatter.getStrategiesJson(strategies.collect(Collectors.toList()));
  }

}
