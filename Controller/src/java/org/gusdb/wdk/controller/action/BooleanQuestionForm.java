package org.gusdb.wdk.controller.action;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.jspwrap.QuestionBean;
import org.gusdb.wdk.model.jspwrap.BooleanQuestionNodeBean;
import org.gusdb.wdk.model.jspwrap.BooleanQuestionLeafBean;
import org.gusdb.wdk.model.jspwrap.ParamBean;
import org.gusdb.wdk.model.jspwrap.FlatVocabParamBean;
import org.gusdb.wdk.controller.CConstants;

/**
 *  form bean for setting up a boolean question
 */

public class BooleanQuestionForm extends QuestionForm {

    private BooleanQuestionNodeBean rootNode = null;
    private BooleanQuestionLeafBean seedLeaf = null;
    private int currentNodeId = 0;

    private QuestionBean newQuestion;
    private String operation;
    private String nextBooleanOperation;
    private String nextQuestionOperand;

    public static Map booleanOperatorMap = new HashMap<String, String>();
    {
	booleanOperatorMap.put("union", BooleanQuestionNodeBean.INTERNAL_OR);
	booleanOperatorMap.put("intersect", BooleanQuestionNodeBean.INTERNAL_AND);
	booleanOperatorMap.put("minus", BooleanQuestionNodeBean.INTERNAL_NOT);
    }

    public void reset() {
	//TODO: implement reset to handle the cases
	// where session scope formbean being used by multiple jsp pages
    }

    /**
     * validate the properties that have been sent from the HTTP request,
     * and return an ActionErrors object that encapsulates any validation errors
     */
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
	ActionErrors errors = new ActionErrors();

	String clicked = request.getParameter(CConstants.PBQ_SUBMIT_KEY);
	if (clicked != null && clicked.startsWith(CConstants.PBQ_SUBMIT_GROW_BOOLEAN)) {
	    return errors;
	}

	Object root = request.getSession().getAttribute(CConstants.CURRENT_BOOLEAN_ROOT_KEY);

	if (!(root instanceof BooleanQuestionLeafBean || root instanceof BooleanQuestionNodeBean)) {
	    throw new RuntimeException("expect BooleanQuestion Leaf or Node Bean but got " + root);
	}

	Vector allNodes = new Vector();
	if (root instanceof BooleanQuestionLeafBean) {
	    allNodes.add(root);
	} else {
	    BooleanQuestionNodeBean rootNode = (BooleanQuestionNodeBean)root;
	    allNodes = rootNode.getAllNodes(allNodes);
	}
	
	for (int i = 0; i < allNodes.size(); i++){
	    Object nextNode = allNodes.elementAt(i);
	    if (nextNode instanceof BooleanQuestionLeafBean){		
		BooleanQuestionLeafBean nextLeaf = (BooleanQuestionLeafBean)nextNode;
		Integer leafId = nextLeaf.getLeafId();
		ParamBean[] params = nextLeaf.getQuestion().getParams();
		for (int j=0; j<params.length; j++) {
		    ParamBean p = params[j];
		    try {
			String pKey = leafId.toString() + '_' + p.getName();
			String[] pVals;
			// should we check menu selections?
			if (p instanceof FlatVocabParamBean && ((FlatVocabParamBean)p).getMultiPick().booleanValue()) {
			    pVals = getMyMultiProp(pKey);
			} else {
			    String val = (String)getMyProp(pKey); 
			    pVals = new String[] {val};
			}
			if (pVals != null) {
			    for (int k=0; k<pVals.length; k++) {
				String pVal = pVals[k];
				String errMsg = p.validateValue(pVal);
				if (errMsg != null) {
				    errors.add(pKey,
					       new ActionError("mapped.properties",
							       p.getPrompt() + " \"" + pVal + "\"",
							       "<br>" + errMsg));
				}
			    }
			}
		    } catch (WdkModelException exp) {
			throw new RuntimeException(exp.getMessage());
		    }
		}
	    }
	}
	return errors;
    }

    public int getNextId(){
	currentNodeId++;
	return currentNodeId;
    }

    public String getOperation(){
	return operation;
    }

    public void setOperation(String op){
	this.operation = op;
    }

    public void setBooleanQuestionNode(BooleanQuestionNodeBean bqn) { rootNode = bqn; }
    public BooleanQuestionNodeBean getBooleanQuestionNode() { return rootNode; }

    public void setBooleanQuestionLeaf(BooleanQuestionLeafBean bqf) { seedLeaf = bqf; }
    public BooleanQuestionLeafBean getBooleanQuestionLeaf() { return seedLeaf;}

    public void setNextQuestionOperand(String nextQuestionOperand) {
	this.nextQuestionOperand = nextQuestionOperand;
    }

    public void setNextBooleanOperation(String nextBooleanOperation){
	this.nextBooleanOperation = nextBooleanOperation;
    }

    String getNextQuestionOperand(int leafId) throws WdkModelException {
	Integer lId = new Integer(leafId); 
	return (String)getMyProp(lId.toString() + '_' + CConstants.NEXT_QUESTION_OPERAND_SUFFIX);
    }
    String getNextBooleanOperation(int leafId) throws WdkModelException {
	Integer lId = new Integer(leafId); 
	return (String)getMyProp(lId.toString() + '_' + CConstants.NEXT_BOOLEAN_OPERATION_SUFFIX);
    }
}
