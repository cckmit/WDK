package org.gusdb.wdk.model.filter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.record.PrimaryKeyDefinition;
import org.gusdb.wdk.model.record.attribute.QueryColumnAttributeField;
import org.json.JSONObject;

public abstract class SqlColumnFilter extends ColumnFilter {

  protected static final String COLUMN_PROPERTY = "property";
  protected static final String COLUMN_COUNT = "count";

  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(SqlColumnFilter.class);

  public SqlColumnFilter(String name, QueryColumnAttributeField attribute) {
    super(name, attribute);
  }

  /**
   * 
   * @param inputSql
   *          SQL that provides a set of rows to filter, including: filter, primary key and dynamic columns
   * @param jsValue
   *          The parameter values. This method should validate the JSON, and throw a WdkModelException if
   *          malformed, or a WdkUserException of illegal values.
   * @return Sql that wraps the input, filtering the rows.
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public abstract String getFilterSql(String inputSql, JSONObject jsValue) throws WdkModelException;

  /**
   * 
   * @param inputSql
   *          SQL that provides a set of rows to filter, including: filter, primary key and dynamic columns
   * @return Sql that wraps the input, providing a summary with at least these two columns: "property"
   *         (varchar) and "count" (number), where count is the number of things found for the item named in
   *         property
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public abstract String getSummarySql(String inputSql) throws WdkModelException;

  @Override
  public abstract String getDisplayValue(AnswerValue answer, JSONObject jsValue) throws WdkModelException;

  @Override
  public JSONObject getSummaryJson(AnswerValue answer, String idSql) throws WdkModelException {
    String attributeSql = getAttributeSql(answer, idSql);

    Map<String, Integer> counts = new LinkedHashMap<>();
    // group by the query and get a count

    String sql = getSummarySql(attributeSql);

    ResultSet resultSet = null;
    DataSource dataSource = answer.getAnswerSpec().getQuestion().getWdkModel().getAppDb().getDataSource();
    try {
      resultSet = SqlUtils.executeQuery(dataSource, sql, getKey() + "-summary");
      while (resultSet.next()) {
        String value = resultSet.getString(COLUMN_PROPERTY);
        int count = resultSet.getInt(COLUMN_COUNT);
        counts.put(value, count);
      }
    }
    catch (SQLException ex) {
      throw new WdkModelException(ex);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(resultSet, null);
    }

    return new ListColumnFilterSummary(counts).toJson();

  }

  @Override
  public String getSql(AnswerValue answer, String idSql, JSONObject jsValue) throws WdkModelException {

    String attributeSql = getAttributeSql(answer, idSql);
    String columnName = _attribute.getName();

    StringBuilder sql = new StringBuilder("SELECT idq.*, aq. " + columnName);

    // need to join with idsql here to get extra (dynamic) columns from idq
    PrimaryKeyDefinition pkDef = answer.getAnswerSpec().getQuestion().getRecordClass().getPrimaryKeyDefinition();
    sql.append(" FROM (" + idSql + ") idq, (" + attributeSql + ") aq WHERE ");
    sql.append(pkDef.createJoinClause("idq", "aq"));

    String filterSql = getFilterSql(sql.toString(), jsValue);
    String finalSql =
        "SELECT idq2.* from (" + idSql + ") idq2, " +
        "(" + filterSql + ") filter WHERE " +
        pkDef.createJoinClause("idq2", "filter");

    return finalSql;

  }

}
