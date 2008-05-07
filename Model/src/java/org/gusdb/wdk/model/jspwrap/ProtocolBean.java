package org.gusdb.wdk.model.jspwrap;

import java.util.ArrayList;

public class ProtocolBean {
    
    StepBean latestStep;
    String name;

    public ProtocolBean() {
    }

    public ProtocolBean(StepBean latestStep, String name) {
	this.latestStep = latestStep;
	this.name = name;
    }

    public void setName(String name) {
	this.name = name;
    }
    
    public String getName() {
	return name;
    }

    public StepBean getLatestStep() {
	return latestStep;
    }

    public StepBean getStep(int index) {
	StepBean returnStep = latestStep;
	for (int i = 0; i < index; ++i) {
	    returnStep = returnStep.getPreviousStep();
	}
	return returnStep;
    }

    public StepBean[] getAllSteps() {
	ArrayList<StepBean> allSteps = new ArrayList<StepBean>();
	allSteps = buildAllStepsArray(allSteps, latestStep);
	return (StepBean[]) allSteps.toArray();
    }

    private ArrayList<StepBean> buildAllStepsArray(ArrayList<StepBean> array, StepBean step) {
	if (step.isFirstStep()) {
	    array.add(step);
	}
	else {
	    array = buildAllStepsArray(array, step.getPreviousStep());
	    array.add(step);
	}
	return array;
    }

    public void addStep(StepBean step) {
	if (latestStep != null) {
	    step.setPreviousStep(latestStep);
	    latestStep.setNextStep(step);
	}
	setLatestStep(step);
    }
    
    public void setLatestStep(StepBean step) {
	this.latestStep = step;
    }

}
