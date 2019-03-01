package org.gusdb.wdk.model.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.validation.ValidationLevel;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;

public class StepUtilities {

  @Deprecated
  public static void deleteStrategy(User user, long strategyId) throws WdkModelException {
    ActiveStrategyFactory activeStrategyFactory = user.getSession().getActiveStrategyFactory();
    String strategyKey = Long.toString(strategyId);
    int order = activeStrategyFactory.getOrder(strategyKey);
    if (order > 0)
      activeStrategyFactory.closeActiveStrategy(strategyKey);
    user.getWdkModel().getStepFactory().deleteStrategy(strategyId);
  }

  /**
   * Imports strategy behind strategy key into new strategy owned by this user.
   * The input strategy key is either:
   * <ul>
   *   <li>a strategy signature (generated by a share link)</li>
   *   <li>
   * 
   * @param strategyKey strategy key
   * @return new strategy
   * @throws WdkModelException
   * @throws WdkUserException
   */
  @Deprecated
  public static Strategy importStrategy(User user, String strategyKey) throws WdkModelException, WdkUserException {
    return importStrategy(user, getStrategyByStrategyKey(user.getWdkModel(), strategyKey, ValidationLevel.NONE), null);
  }

  @Deprecated
  public static Strategy getStrategyByStrategyKey(WdkModel wdkModel, String strategyKey, ValidationLevel level)
      throws WdkModelException, WdkUserException {
    Strategy oldStrategy;
    String[] parts = strategyKey.split(":");
    if (parts.length == 1) {
      // new strategy export url
      String strategySignature = parts[0];
      oldStrategy = wdkModel.getStepFactory().getStrategyBySignature(strategySignature)
          .orElseThrow(() -> new WdkUserException("Could not find strategy with signature " + strategySignature));
    }
    else {
      // get user from user signature
      User owner = wdkModel.getUserFactory().getUserBySignature(parts[0]);
      // make sure strategy id is an integer
      String strategyIdStr = parts[1];
      if (!FormatUtil.isInteger(strategyIdStr)) {
        throw new WdkUserException("Invalid strategy ID: " + strategyIdStr);
      }
      oldStrategy = wdkModel.getStepFactory().getStrategyById(Long.parseLong(strategyIdStr), level).orElse(null);
      if (oldStrategy == null || oldStrategy.getUser().getUserId() != owner.getUserId()) {
        throw new WdkUserException("Can not find strategy " + strategyIdStr + " for user " + owner.getUserId());
      }
    }
    return oldStrategy;
  }

  @Deprecated
  public static Strategy importStrategy(User user, Strategy oldStrategy, Map<Long, Long> stepIdsMap)
      throws WdkModelException {
    Strategy newStrategy = user.getWdkModel().getStepFactory().copyStrategy(user, oldStrategy, stepIdsMap);
    // highlight the imported strategy
    long rootStepId = newStrategy.getRootStepId();
    String strategyKey = Long.toString(newStrategy.getStrategyId());
    if (newStrategy.isValid())
      user.getSession().setViewResults(strategyKey, rootStepId, 0);
    return newStrategy;
  }

  @Deprecated
  public static TwoTuple<List<Strategy>, List<String>> getOpenableStrategies(WdkModel wdkModel, List<String> stratKeys) {
    List<Strategy> successfulStrats = new ArrayList<>();
    List<String> failedStratKeys = new ArrayList<>();
    for (String stratKey : stratKeys) {
      try {
        Strategy strat = getStrategyByStrategyKey(wdkModel, stratKey, ValidationLevel.NONE);
        successfulStrats.add(strat);
      }
      catch (Exception e) {
        failedStratKeys.add(stratKey);
      }
    }
    return new TwoTuple<>(successfulStrats, failedStratKeys);
  }

  @Deprecated
  public static Strategy getStrategy(User user, long strategyId, ValidationLevel level)
      throws WdkUserException, WdkModelException {
    return user.getWdkModel().getStepFactory()
      .getStrategyById(strategyId, level)
      .filter(strategy -> strategy.getUser().getUserId() == user.getUserId())
      .orElseThrow(() -> new WdkUserException(
        "No strategy with ID " + strategyId + " exists for user " + user.getUserId()));
  }

  @Deprecated
  public static Step getStep(User user, long stepId, ValidationLevel validationLevel)
      throws WdkUserException, WdkModelException {
    return user.getWdkModel().getStepFactory()
        .getStepById(stepId, validationLevel)
        .filter(step -> step.getUser().getUserId() == user.getUserId())
        .orElseThrow(() -> new WdkUserException(
          "No step with ID " + stepId + " exists for user " + user.getUserId()));
  }
}
