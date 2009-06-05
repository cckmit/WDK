/**
 * 
 */
package org.gusdb.wdk.controller.action;

import java.net.URLEncoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.gusdb.wdk.controller.CConstants;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.jspwrap.StrategyBean;
import org.gusdb.wdk.model.jspwrap.UserBean;

/**
 * @author ctreatma
 * 
 */
public class ProcessRenameStrategyAction extends Action {

    private static Logger logger = Logger.getLogger(ProcessRenameStrategyAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        logger.debug("Entering Rename Strategy...");

        UserBean wdkUser = ActionUtility.getUser(servlet, request);
        try {
            String state = request.getParameter(CConstants.WDK_STATE_KEY);

            String strStratId = request.getParameter(CConstants.WDK_STRATEGY_ID_KEY);
            String customName = request.getParameter("name");
            boolean save = Boolean.valueOf(request.getParameter("save")).booleanValue();
            boolean checkName = Boolean.valueOf(
                    request.getParameter("checkName")).booleanValue();
            // TEST
            if (customName == null || customName.length() == 0) {
                throw new Exception("No name was given for saving Strategy.");
            }
            if (strStratId == null || strStratId.length() == 0) {
                throw new Exception("No Strategy was given for saving");
            }

            int stratId = Integer.parseInt(strStratId);
            StrategyBean strategy = wdkUser.getStrategy(stratId);
            boolean opened = (wdkUser.getStrategyOrder(strStratId) > 0);

            // verify the checksum
            String checksum = request.getParameter(CConstants.WDK_STRATEGY_CHECKSUM_KEY);
            if (checksum != null && !strategy.getChecksum().equals(checksum)) {
                ShowStrategyAction.outputOutOfSyncJSON(wdkUser, response, state);
                return null;
            }

            // if we haven't been asked to check the user-specified name, or a
            // strategy with that name does not already exist, do the rename
            if (!checkName
                    || !wdkUser.checkNameExists(strategy, customName, save)) {
                logger.debug("failed check.  either not checking name, or strategy doesn't already exist.");
                int oldStrategyId = strategy.getStrategyId();

                if (save) {
                    if (wdkUser.isGuest()) {
                        throw new Exception(
                                "You must be logged in to save a strategy!");
                    }
                    // if we're saving, and the strat is already saved (which
                    // means
                    // savedName is not null),
                    // and the new name to save with is different from the
                    // savedName
                    // (which means we're
                    // doing a "save as"), then make a new copy of this
                    // strategy.
                    if (strategy.getIsSaved()
                            && !customName.equals(strategy.getSavedName()))
                        strategy = wdkUser.createStrategy(
                                strategy.getLatestStep(), false);

                    // mark the strategy as saved, set saved name
                    strategy.setIsSaved(true);
                }

                // whether its a save or rename, set new name specified by user.
                strategy.setName(customName);
		strategy.setSavedName(customName);
                strategy.update(save || strategy.getIsSaved());

                try {
                    wdkUser.replaceActiveStrategy(
                            oldStrategyId,
                            strategy.getStrategyId(), null);
                } catch (WdkUserException ex) {
                    // Adding active strat will be handled by ShowStrategyAction
                }

                request.setAttribute(CConstants.WDK_STEP_KEY,
                        strategy.getLatestStep());
                request.setAttribute(CConstants.WDK_STRATEGY_KEY, strategy);
            } else {    // name already exists
                ShowStrategyAction.outputDuplcicateNameJSON(wdkUser, response, state);
                return null;
            }

            // forward to strategyPage.jsp
            ActionForward showStrategy = mapping.findForward(CConstants.SHOW_STRATEGY_MAPKEY);
            StringBuffer url = new StringBuffer(showStrategy.getPath());
            url.append("?state=" + URLEncoder.encode(state, "UTF-8"));
            if (!opened)
                url.append("&").append(CConstants.WDK_OPEN_KEY).append("=false");

            ActionForward forward = new ActionForward(url.toString());
            return forward;
        } catch (Exception ex) {
            logger.error(ex);
            ex.printStackTrace();
            ShowStrategyAction.outputErrorJSON(wdkUser, response, ex);
            return null;
        }
    }

}
