package org.gusdb.wdk.model.query.param;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gusdb.wdk.model.AnswerValue;
import org.gusdb.wdk.model.RecordClass;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.user.Step;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author xingao
 * 
 *         raw data: same as user-dependent data, it is a step id;
 * 
 *         user-dependent data: same as raw data, a step id;
 * 
 *         user-independent data: a key such as: answer_checksum:filter_name;
 *         the ":filter_name" is optional;
 * 
 *         internal data: a sql that represents the cached result
 * 
 */
public class AnswerParam extends Param {

    private List<RecordClassReference> recordClassRefs;
    private Map<String, RecordClass> recordClasses;

    public AnswerParam() {
        recordClassRefs = new ArrayList<RecordClassReference>();
        recordClasses = new LinkedHashMap<String, RecordClass>();
    }

    private AnswerParam(AnswerParam param) {
        super(param);
        if (param.recordClassRefs != null)
            this.recordClassRefs = new ArrayList<RecordClassReference>(
                    param.recordClassRefs);
        if (param.recordClasses != null)
            this.recordClasses = new LinkedHashMap<String, RecordClass>(
                    param.recordClasses);
    }

    // ///////////////////////////////////////////////////////////////
    // protected methods
    // ///////////////////////////////////////////////////////////////

    /**
     * @param recordClassRef
     *            the recordClassRef to set
     */
    public void addRecordClassRef(RecordClassReference recordClassRef) {
        this.recordClassRefs.add(recordClassRef);
    }

    /**
     * @return the recordClass
     */
    public Map<String, RecordClass> getRecordClasses() {
        return new LinkedHashMap<String, RecordClass>(recordClasses);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.Param#clone()
     */
    @Override
    public Param clone() {
        return new AnswerParam(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.Param#resolveReferences(org.gusdb.wdk.model.WdkModel)
     */
    @Override
    public void resolveReferences(WdkModel model) throws WdkModelException {
        if (resolved) return;

        this.wdkModel = model;

        // resolve recordClass ref
        for (RecordClassReference reference : recordClassRefs) {
            String rcName = reference.getRef();
            RecordClass recordClass = model.getRecordClass(rcName);
            this.recordClasses.put(rcName, recordClass);
        }
        this.recordClassRefs = null;

        // make sure all record classes has the same primary key definition
        RecordClass recordClass = recordClasses.values().iterator().next();
        String[] columns = recordClass.getPrimaryKeyAttributeField().getColumnRefs();
        Set<String> set = new HashSet<String>();
        for (String column : columns) {
            set.add(column);
        }
        for (RecordClass rc : recordClasses.values()) {
            String message = "The recordClasses referred in answerParam "
                    + getFullName()
                    + " doesn't have same primary key definitions.";
            columns = rc.getPrimaryKeyAttributeField().getColumnRefs();
            if (columns.length != set.size())
                throw new WdkModelException(message);
            for (String column : columns) {
                if (!set.contains(column))
                    throw new WdkModelException(message);
            }
        }

        this.resolved = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.Param#appendJSONContent(org.json.JSONObject)
     */
    @Override
    protected void appendJSONContent(JSONObject jsParam, boolean extra)
            throws JSONException {
    // add recordClass ref
    // jsParam.put("recordClass", recordClassRef);
    }

    public AnswerValue getAnswerValue(User user, String dependentValue)
            throws WdkModelException, SQLException, NoSuchAlgorithmException,
            JSONException, WdkUserException {

        // check format
        int stepId = Integer.parseInt(dependentValue);
        Step step = user.getStep(stepId);
        return step.getAnswerValue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#dependentValueToIndependentValue
     * (org.gusdb.wdk.model.user.User, java.lang.String)
     */
    @Override
    public String dependentValueToIndependentValue(User user,
            String dependentValue) throws NoSuchAlgorithmException,
            WdkUserException, WdkModelException, SQLException, JSONException {
        int stepId = Integer.parseInt(dependentValue);
        Step step = user.getStep(stepId);
        return step.getAnswerKey();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#independentValueToInternalValue
     * (java.lang.String)
     */
    @Override
    public String dependentValueToInternalValue(User user, String dependentValue)
            throws WdkModelException, NoSuchAlgorithmException, SQLException,
            JSONException, WdkUserException {
        int stepId = Integer.parseInt(dependentValue);

        if (isNoTranslation()) return Integer.toString(stepId);

        Step step = user.getStep(stepId);
        AnswerValue answerValue = step.getAnswerValue();
        return "(" + answerValue.getIdSql() + ")";
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#independentValueToRawValue(java
     * .lang.String)
     */
    @Override
    public String dependentValueToRawValue(User user, String dependentValue)
            throws WdkModelException, NoSuchAlgorithmException,
            WdkUserException, SQLException, JSONException {
        return dependentValue;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#rawValueToIndependentValue(java
     * .lang.String)
     */
    @Override
    public String rawOrDependentValueToDependentValue(User user, String rawValue)
            throws NoSuchAlgorithmException, WdkModelException,
            WdkUserException, SQLException, JSONException {
        return rawValue;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#validateValue(org.gusdb.wdk.model
     * .user.User, java.lang.String)
     */
    @Override
    protected void validateValue(User user, String dependentValue)
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        int stepId = Integer.parseInt(dependentValue);
        Step step = user.getStep(stepId);
        // try to get the answer value
        step.getAnswerValue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#excludeResources(java.lang.String)
     */
    @Override
    public void excludeResources(String projectId) throws WdkModelException {
        super.excludeResources(projectId);

        for (int i = recordClassRefs.size() - 1; i >= 0; i--) {
            RecordClassReference reference = recordClassRefs.get(i);
            if (!reference.include(projectId)) recordClassRefs.remove(i);
        }
        if (recordClassRefs.size() == 0)
            throw new WdkModelException("No recordClass ref is defined in "
                    + "answerParam " + getFullName() + " for project "
                    + projectId);
    }
}
