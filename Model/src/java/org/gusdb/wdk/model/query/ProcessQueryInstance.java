package org.gusdb.wdk.model.query;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;
import javax.xml.rpc.ServiceException;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.dbms.ArrayResultList;
import org.gusdb.wdk.model.dbms.CacheFactory;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wsf.client.WsfServiceServiceLocator;
import org.gusdb.wsf.service.WsfRequest;
import org.gusdb.wsf.service.WsfServiceException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This process query instance calls the web service, retrieves the result, and
 * cache them into the cache table. If the result generated by the service is
 * too big, the service will send it in multiple packets, and the process query
 * instance will retrieve all the packets in order.
 * 
 * @author Jerric Gao
 */
public class ProcessQueryInstance extends QueryInstance {

  private static final Logger logger = Logger.getLogger(ProcessQueryInstance.class);

  private static final int CACHE_INSERT_BATCH_SIZE = 1000;
  
  private ProcessQuery query;
  private int signal;

  public ProcessQueryInstance(User user, ProcessQuery query,
      Map<String, String> values, boolean validate, int assignedWeight,
      Map<String, String> context) throws WdkModelException {
    super(user, query, values, validate, assignedWeight, context);
    this.query = query;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.gusdb.wdk.model.query.QueryInstance#appendSJONContent(org.json.JSONObject
   * )
   */
  @Override
  protected void appendSJONContent(JSONObject jsInstance) throws JSONException {
    jsInstance.put("signal", signal);
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.gusdb.wdk.model.query.QueryInstance#insertToCache(java.sql.Connection ,
   * java.lang.String)
   */
  @Override
  public void insertToCache(String tableName, int instanceId)
      throws WdkModelException {
    logger.debug("inserting process query result to cache...");
    Map<String, Column> columns = query.getColumnMap();
    String weightColumn = Utilities.COLUMN_WEIGHT;

    // prepare the sql
    StringBuffer sql = new StringBuffer("INSERT INTO ");
    sql.append(tableName);
    sql.append(" (");
    sql.append(CacheFactory.COLUMN_INSTANCE_ID);
    // have to move clobs to the end of insert
    for (Column column : columns.values()) {
      if (column.getType() != ColumnType.CLOB)
        sql.append(", " + column.getName());
    }
    for (Column column : columns.values()) {
      if (column.getType() == ColumnType.CLOB)
        sql.append(", " + column.getName());
    }

    if (query.isHasWeight() && !columns.containsKey(weightColumn))
      sql.append(", " + weightColumn);
    sql.append(") VALUES (");
    sql.append(instanceId);
    for (int i = 0; i < columns.size(); i++) {
      sql.append(", ?");
    }
    // insert weight to the last column, if doesn't exist
    if (query.isHasWeight() && !columns.containsKey(weightColumn))
      sql.append(", " + assignedWeight);
    sql.append(")");

    DBPlatform platform = query.getWdkModel().getAppDb().getPlatform();
    PreparedStatement ps = null;
    try {
      DataSource dataSource = wdkModel.getAppDb().getDataSource();
      ps = SqlUtils.getPreparedStatement(dataSource, sql.toString());
      long startTime = System.currentTimeMillis();
      ResultList resultList = getUncachedResults();
      logger.info("Getting uncached results took "
          + ((System.currentTimeMillis() - startTime) / 1000D) + " seconds");
      startTime = System.currentTimeMillis();
      int rowsInBatch = 0, numBatches = 0;
      long cumulativeBatchTime = 0;
      while (resultList.next()) {
        int columnId = 1;
        // have to move clobs to the end
        for (Column column : columns.values()) {
          ColumnType type = column.getType();
          if (type == ColumnType.CLOB)
            continue;

          String value = (String) resultList.get(column.getName());

          // determine the type
          if (type == ColumnType.BOOLEAN) {
            ps.setBoolean(columnId, Boolean.parseBoolean(value));
          } else if (type == ColumnType.DATE) {
            ps.setTimestamp(columnId, new Timestamp(
                Date.valueOf(value).getTime()));
          } else if (type == ColumnType.FLOAT) {
            ps.setFloat(columnId, Float.parseFloat(value));
          } else if (type == ColumnType.NUMBER) {
            ps.setInt(columnId, Integer.parseInt(value));
          } else {
            int width = column.getWidth();
            if (value != null && value.length() > width) {
              logger.warn("Column [" + column.getName() + "] value truncated.");
              value = value.substring(0, width - 3) + "...";
            }
            ps.setString(columnId, value);
          }
          columnId++;
        }
        for (Column column : columns.values()) {
          if (column.getType() == ColumnType.CLOB) {
            String value = (String) resultList.get(column.getName());
            platform.setClobData(ps, columnId, value, false);
            columnId++;
          }
        }
        ps.addBatch();

        rowsInBatch++;
        if (rowsInBatch == CACHE_INSERT_BATCH_SIZE) {
          numBatches++;
          cumulativeBatchTime = executeBatchWithLogging(ps, numBatches,
                  rowsInBatch, cumulativeBatchTime);
          rowsInBatch = 0;
        }
      }
      if (rowsInBatch > 0) {
        numBatches++;
        cumulativeBatchTime = executeBatchWithLogging(ps, numBatches,
            rowsInBatch, cumulativeBatchTime);
      }
      long cumulativeInsertTime = System.currentTimeMillis() - startTime;
      logger.info("All batches completed.\nInserting results to cache took " +
          (cumulativeInsertTime / 1000D) + " seconds (Java + Oracle clock time)\n" +
          (cumulativeBatchTime / 1000D) + " seconds of that were spent executing batches (" +
          FormatUtil.getPctFromRatio(cumulativeBatchTime, cumulativeInsertTime) + ")");
    }
    catch (SQLException e) {
      throw new WdkModelException("Unable to insert record into cache.", e);
    }
    finally {
      SqlUtils.closeStatement(ps);
    }
    logger.debug("process query cache insertion finished.");
  }
  
  private long executeBatchWithLogging(PreparedStatement ps, int numBatches,
      int rowsInBatch, long cumulativeBatchTime) throws SQLException {
    long batchStart = System.currentTimeMillis();
    ps.executeBatch();
    long batchElapsed = System.currentTimeMillis() - batchStart;
    cumulativeBatchTime += batchElapsed;
    logger.info("Writing batch " + numBatches + " (" + rowsInBatch +
        " records) took " + batchElapsed + " ms.  Cumulative batch " +
        "execution time: " + cumulativeBatchTime + " ms");
    return cumulativeBatchTime;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#getUncachedResults(org.gusdb.
   * wdk.model.Column[], java.lang.Integer, java.lang.Integer)
   */
  @Override
  protected ResultList getUncachedResults() throws WdkModelException {
    WsfRequest request = new WsfRequest();
    request.setPluginClass(query.getProcessName());
    request.setProjectId(wdkModel.getProjectId());

    // prepare parameters
    Map<String, String> paramValues = getInternalParamValues();
    HashMap<String, String> params = new HashMap<String, String>();
    for (String name : paramValues.keySet()) {
      params.put(name, paramValues.get(name));
    }
    request.setParams(params);

    // prepare columns
    Map<String, Column> columns = query.getColumnMap();
    String[] columnNames = new String[columns.size()];
    Map<String, Integer> indices = new LinkedHashMap<String, Integer>();
    columns.keySet().toArray(columnNames);
    String temp = "";
    for (int i = 0; i < columnNames.length; i++) {
      // if the wsName is defined, reassign it to the columns
      Column column = columns.get(columnNames[i]);
      if (column.getWsName() != null)
        columnNames[i] = column.getWsName();
      indices.put(column.getName(), i);
      temp += columnNames[i] + ", ";
    }
    request.setOrderedColumns(columnNames);
    logger.debug("process query columns: " + temp);

    request.setContext(context);

    StringBuffer resultMessage = new StringBuffer();
    try {
      ProcessResponse response = getResponse(request, query.isLocal());
      this.resultMessage = response.getMessage();
      this.signal = response.getSignal();
      String[][] content = response.getResult();

      logger.debug("WSQI Result Message:" + resultMessage);
      logger.info("Result Array size = " + content.length);

      // add weight if needed
      String weightColumn = Utilities.COLUMN_WEIGHT;
      if (query.isHasWeight() && !columns.containsKey(weightColumn)) {
        indices.put(weightColumn, indices.size());
        for (int i = 0; i < content.length; i++) {
          String[] line = content[i];
          String[] newLine = new String[line.length + 1];
          System.arraycopy(line, 0, newLine, 0, line.length);
          newLine[line.length] = Integer.toString(assignedWeight);
          content[i] = newLine;
        }
      }

      ArrayResultList result = new ArrayResultList(response, indices);
      result.setHasWeight(query.isHasWeight());
      result.setAssignedWeight(assignedWeight);
      return result;
    } catch (RemoteException | MalformedURLException | ServiceException
        | WsfServiceException ex) {
      throw new WdkModelException(ex);
    }
  }

  private ProcessResponse getResponse(WsfRequest request, boolean local)
      throws RemoteException, MalformedURLException, ServiceException,
      WsfServiceException {

    long start = System.currentTimeMillis();
    String jsonRequest = request.toString();
    ProcessResponse response;

    if (local) { // invoke the process query locally
      logger.info("Using local service");
      // call the service directly
      org.gusdb.wsf.service.WsfService service = new org.gusdb.wsf.service.WsfService();
      org.gusdb.wsf.service.WsfResponse wsfResponse = service.invoke(jsonRequest);
      response = new ServiceProcessResponse(service, wsfResponse);
    }
    
    else { // invoke the process query via web service
      logger.info("Using remote service");
      // call the service through client
      String serviceUrl = query.getWebServiceUrl();
      logger.info("Invoking " + request.getPluginClass() + " at " + serviceUrl);
      WsfServiceServiceLocator locator = new WsfServiceServiceLocator();
      org.gusdb.wsf.client.WsfService client = locator.getWsfService(new URL(serviceUrl));
      org.gusdb.wsf.client.WsfResponse wsfResponse = client.invoke(jsonRequest);
      response = new ClientProcessResponse(client, wsfResponse);
    }
    long end = System.currentTimeMillis();
    logger.debug("Client took " + ((end - start) / 1000.0) + " seconds.");

    return response;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#getSql()
   */
  @Override
  public String getSql() throws WdkModelException {
    // always get sql that queries on the cached result
    return getCachedSql();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.gusdb.wdk.model.query.QueryInstance#createCache(java.sql.Connection,
   * java.lang.String, int)
   */
  @Override
  public void createCache(String tableName, int instanceId)
      throws WdkModelException {
    logger.debug("creating process query cache...");
    DBPlatform platform = query.getWdkModel().getAppDb().getPlatform();
    Column[] columns = query.getColumns();

    StringBuffer sqlTable = new StringBuffer("CREATE TABLE ");
    sqlTable.append(tableName).append(" (");

    // define the instance id column
    String numberType = platform.getNumberDataType(12);
    sqlTable.append(CacheFactory.COLUMN_INSTANCE_ID + " " + numberType);
    sqlTable.append(" NOT NULL");
    if (query.isHasWeight())
      sqlTable.append(", " + Utilities.COLUMN_WEIGHT + " " + numberType);

    // define the rest of the columns
    for (Column column : columns) {
      // weight column is already added to the sql.
      if (column.getName().equals(Utilities.COLUMN_WEIGHT)
          && query.isHasWeight())
        continue;

      int width = column.getWidth();
      ColumnType type = column.getType();

      String strType;
      if (type == ColumnType.BOOLEAN) {
        strType = platform.getBooleanDataType();
      } else if (type == ColumnType.CLOB) {
        strType = platform.getClobDataType();
      } else if (type == ColumnType.DATE) {
        strType = platform.getDateDataType();
      } else if (type == ColumnType.FLOAT) {
        strType = platform.getFloatDataType(width);
      } else if (type == ColumnType.NUMBER) {
        strType = platform.getNumberDataType(width);
      } else if (type == ColumnType.STRING) {
        strType = platform.getStringDataType(width);
      } else {
        throw new WdkModelException("Unknown data type [" + type
            + "] of column [" + column.getName() + "]");
      }

      sqlTable.append(", " + column.getName() + " " + strType);
    }
    sqlTable.append(")");

    try {
      DataSource dataSource = wdkModel.getAppDb().getDataSource();
      SqlUtils.executeUpdate(dataSource, sqlTable.toString(),
          query.getFullName() + "__create-cache-table");
    } catch (SQLException e) {
      throw new WdkModelException("Unable to create cache table.", e);
    }
    // also insert the result into the cache
    insertToCache(tableName, instanceId);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#getResultSize()
   */
  @Override
  public int getResultSize() throws WdkModelException {
    if (!isCached()) {
      int count = 0;
      ResultList resultList = getResults();
      while (resultList.next()) {
        count++;
      }
      return count;
    } else
      return super.getResultSize();
  }

}
