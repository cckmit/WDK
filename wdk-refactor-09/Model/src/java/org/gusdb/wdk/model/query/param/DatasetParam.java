/**
 * 
 */
package org.gusdb.wdk.model.query.param;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import org.gusdb.wdk.model.RecordClass;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.user.Dataset;
import org.gusdb.wdk.model.user.DatasetFactory;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author xingao
 * 
 *         raw data: a comma separated list of entities;
 * 
 *         user-dependent data: user dataset id;
 * 
 *         user-independent data: dataset checksum;
 * 
 *         internal data: dataset id; in the future the return will be a SQL
 *         that represents the
 * 
 */
public class DatasetParam extends Param {

    private String columnName = DatasetFactory.COLUMN_DATASET_VALUE;

    private String recordClassRef;
    private RecordClass recordClass;

    public DatasetParam() {}

    public DatasetParam(DatasetParam param) {
        super(param);
        this.columnName = param.columnName;
        this.recordClass = param.recordClass;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.Param#resolveReferences(org.gusdb.wdk.model.WdkModel)
     */
    @Override
    public void resolveReferences(WdkModel model) throws WdkModelException {
        this.wdkModel = model;
        if (recordClassRef != null) {
            recordClass = (RecordClass) wdkModel.resolveReference(recordClassRef);
            recordClassRef = null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.Param#clone()
     */
    @Override
    public Param clone() {
        return new DatasetParam(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.Param#appendJSONContent(org.json.JSONObject)
     */
    @Override
    protected void appendJSONContent(JSONObject jsParam, boolean extra)
            throws JSONException {
        if (extra) {
            jsParam.put("column", columnName);
        }
    }

    /**
     * @return the columnName
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * @param columnName
     *            the columnName to set
     */
    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    /**
     * convert from user dataset id to dataset checksum
     * 
     * @see org.gusdb.wdk.model.query.param.Param#dependentValueToIndependentValue(org.gusdb.wdk.model.user.User,
     *      java.lang.String)
     */
    @Override
    public String dependentValueToIndependentValue(User user,
            String dependentValue) throws NoSuchAlgorithmException,
            WdkUserException, WdkModelException, SQLException, JSONException {
        logger.debug("dependent to independent: " + dependentValue);
        int userDatasetId = Integer.parseInt(dependentValue);
        Dataset dataset = user.getDataset(userDatasetId);
        return dataset.getChecksum();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#independentValueToInternalValue
     * (org.gusdb.wdk.model.user.User, java.lang.String)
     */
    @Override
    public String dependentValueToInternalValue(User user, String dependentValue)
            throws WdkModelException, NoSuchAlgorithmException, SQLException,
            JSONException, WdkUserException {
        int userDatasetId = Integer.parseInt(dependentValue);
        Dataset dataset = user.getDataset(userDatasetId);
        return Integer.toString(dataset.getDatasetId());

        // to be compatible with previous model, it returns dataset_id;
        // in the future, should return a nested SQL that represents the result

        // ModelConfig config = wdkModel.getModelConfig();
        // String dbLink = config.getApplicationDB().getUserDbLink();
        // String wdkSchema = config.getUserDB().getWdkEngineSchema();
        //
        // StringBuffer sql = new StringBuffer("SELECT ");
        // sql.append(DatasetFactory.COLUMN_DATASET_VALUE).append(" AS ");
        // sql.append(columnName);
        // sql.append(" FROM ").append(wdkSchema);
        // sql.append(DatasetFactory.TABLE_DATASET_VALUE).append(dbLink);
        // sql.append(" WHERE ").append(DatasetFactory.COLUMN_DATASET_ID);
        // sql.append(" = ").append(datasetId);
        // return sql.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#independentValueToRawValue(org.
     * gusdb.wdk.model.user.User, java.lang.String)
     */
    @Override
    public String dependentValueToRawValue(User user, String dependentValue)
            throws WdkModelException, NoSuchAlgorithmException,
            WdkUserException, SQLException, JSONException {
        logger.debug("dependent to raw: " + dependentValue);
        int userDatasetId = Integer.parseInt(dependentValue);
        Dataset dataset = user.getDataset(userDatasetId);
        String[] values = dataset.getValues();
        return Utilities.fromArray(values);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.param.Param#rawValueToIndependentValue(org.
     * gusdb.wdk.model.user.User, java.lang.String)
     */
    @Override
    public String rawOrDependentValueToDependentValue(User user, String rawValue)
            throws NoSuchAlgorithmException, WdkModelException,
            WdkUserException, SQLException {
        // first assume the input is dependent value, that is, user dataset id
        if (rawValue == null || rawValue.length() == 0) return null;
        if (rawValue.matches("\\d+")) {
            int userDatasetId = Integer.parseInt(rawValue);
            try {
                user.getDataset(userDatasetId);
                return rawValue;
            } catch (Exception ex) {
                // dataset doesn't exist, create one
                logger.info("user dataset id doesn't exist: " + userDatasetId);
            }
        }
        return rawValueToDependentValue(user, "", rawValue);
    }

    /**
     * @param user
     * @param uploadFile
     * @param rawValue
     * @return
     * @throws NoSuchAlgorithmException
     * @throws WdkUserException
     * @throws WdkModelException
     * @throws SQLException
     */
    public String rawValueToDependentValue(User user, String uploadFile,
            String rawValue) throws NoSuchAlgorithmException, WdkUserException,
            WdkModelException, SQLException {
        logger.debug("raw to dependent: " + rawValue);
        String[] values = Utilities.toArray(rawValue);
        Dataset dataset = user.createDataset(uploadFile, values);
        return Integer.toString(dataset.getUserDatasetId());
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
            throws WdkModelException, NoSuchAlgorithmException, SQLException,
            JSONException, WdkUserException {
        // try to get the dataset
        int userDatasetId = Integer.parseInt(dependentValue);
        user.getDataset(userDatasetId);
    }

    /**
     * @return the recordClass
     */
    public RecordClass getRecordClass() {
        return recordClass;
    }

    /**
     * @param recordClassRef
     *            the recordClassRef to set
     */
    public void setRecordClassRef(String recordClassRef) {
        this.recordClassRef = recordClassRef;
    }

}
