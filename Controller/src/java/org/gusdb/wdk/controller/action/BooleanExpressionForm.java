package org.gusdb.wdk.controller.action;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.gusdb.wdk.model.jspwrap.UserBean;

/**
 * form bean for holding the boolean expression string fro queryHistory.jsp page
 */

public class BooleanExpressionForm extends ActionForm {

    /**
     * 
     */
    private static final long serialVersionUID = -6371621860440022826L;

    private static Logger logger = Logger.getLogger(BooleanExpressionForm.class);

    private String booleanExpression = null;
    private String historySectionId = null;

    private boolean useBooleanFilter = true;

    public void setBooleanExpression(String be) {
        booleanExpression = be;
    }

    public String getBooleanExpression() {
        return booleanExpression;
    }

    public void setHistorySectionId(String si) {
        historySectionId = si;
    }

    public String getHistorySectionId() {
        return historySectionId;
    }

    /**
     * @return the useBooleanFilter
     */
    public boolean isUseBooleanFilter() {
        return useBooleanFilter;
    }

    /**
     * @param useBooleanFilter
     *            the useBooleanFilter to set
     */
    public void setUseBooleanFilter(boolean useBooleanFilter) {
        this.useBooleanFilter = useBooleanFilter;
    }

    /**
     * validate the properties that have been sent from the HTTP request, and
     * return an ActionErrors object that encapsulates any validation errors
     */
    public ActionErrors validate(ActionMapping mapping,
            HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        String errMsg = null;
        try {
            UserBean wdkUser = ActionUtility.getUser(getServlet(), request);
            String expression = getBooleanExpression();

            logger.info("Validating boolean expression: " + expression);

            wdkUser.validateExpression(expression, useBooleanFilter);
            if (errMsg != null) {
                errors.add(ActionErrors.GLOBAL_ERROR, new ActionError(
                        "mapped.properties", "booleanExpression", errMsg));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            errMsg = ex.getMessage();
            errors.add(ActionErrors.GLOBAL_ERROR, new ActionError(
                    "mapped.properties", "booleanExpression", errMsg));
        }

        return errors;
    }
}
