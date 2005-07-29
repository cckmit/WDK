package org.gusdb.wdk.model.implementation;

import org.gusdb.wdk.model.ResultFactory;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.ResultList;
import org.gusdb.wdk.model.Query;
import org.gusdb.wdk.model.Column;
import org.gusdb.wdk.model.DerivedColumnI;
import org.gusdb.wdk.model.QueryInstance;

import java.util.HashMap;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Clob;

public class SqlResultList extends ResultList {

    ResultSet resultSet;

    public SqlResultList(QueryInstance instance,
			 String resultTableName, ResultSet resultSet) {
        super(instance, resultTableName);
        this.resultSet = resultSet;
    }


    public Object getMultiModeIValue() throws WdkModelException{
	Object o;
	try {
	     o = resultSet.getObject(ResultFactory.MULTI_MODE_I);
	}
	catch (SQLException e){
	    throw new WdkModelException(e);
	}
	return o;
    }

    public Object getValueFromResult(String attributeName) throws WdkModelException {
        Object o = null;
        try {
	    ResultSetMetaData rsmd = resultSet.getMetaData();
	    int columnIndex = resultSet.findColumn(attributeName);
	    int columnType = rsmd.getColumnType(columnIndex);
	    if (columnType == Types.CLOB){
		Clob clob = resultSet.getClob(attributeName);
		Long length = new Long(clob.length());
		o = clob.getSubString(1, length.intValue());
	    }
	    else{
		o = resultSet.getObject(attributeName);
	    }
        } catch (SQLException e) {
            throw new WdkModelException(e);
        }

	if (o == null) {
	    o = "";
	}

        return o;
    }

    public boolean next() throws WdkModelException {
        boolean b = false;
        try {
            b = resultSet.next();
        } catch (SQLException e) {
            throw new WdkModelException(e);
        }
        return b;
    }

    public void print() throws WdkModelException {
	try {
	    SqlUtils.printResultSet(resultSet);
	} catch (SQLException e) {
	    throw new WdkModelException(e);
	}
    }

    public void close() throws WdkModelException {
	try {
	    SqlUtils.closeResultSet(resultSet);
	} catch (SQLException e) {
	    throw new WdkModelException(e);
	}
    }

    public void checkQueryColumns(Query query, boolean checkAll, boolean has_multi_mode_i) throws WdkModelException {

	try {
	    boolean sqlHasIcolumn = false;
	    HashMap rsCols = new HashMap();
	    ResultSetMetaData metaData = resultSet.getMetaData();
	    int rsColCount = metaData.getColumnCount();
	    for (int i=1; i<=rsColCount; i++) {
		String columnName = metaData.getColumnName(i).toLowerCase();
		//check if sql is being retrieved from a result table that has an extra column named 'i' for 
		//enumerating results in the table (this extra column will be ignored when doing column validation)
		if (columnName.equals(ResultFactory.MULTI_MODE_I) && has_multi_mode_i) {
		    sqlHasIcolumn = true;
		}
		rsCols.put(columnName, "");
	    }
	
	    HashMap alreadySeen = new HashMap();
	    Column[] columns = query.getColumns();
	    String queryName = query.getFullName();
	    int colCount = 0;
	    for (int i=0; i<columns.length; i++) {
		if (columns[i] instanceof DerivedColumnI) continue;
		colCount++;
		String columnName = columns[i].getName();
		if (alreadySeen.containsKey(columnName)) 
		    throw new WdkModelException("Query '" + queryName + "' declares duplicate columns named '" + columnName + "'");
		alreadySeen.put(columnName, "");
		if (!rsCols.containsKey(columnName)) 
		    throw new WdkModelException("Query '" + queryName + "' declares column '" + columnName + "' but it is not in the Sql");

	    }

	    if (checkAll) {
		if ((rsColCount != colCount && sqlHasIcolumn == false) || (rsColCount != colCount + 1 && sqlHasIcolumn == true)) 
		    throw new WdkModelException("Query '" + queryName + "' declares a different number of columns(" + colCount + ") than are mentioned in the Sql (" + rsColCount + ")");
	    } else {
		if (rsColCount < colCount) 
		    throw new WdkModelException("Query '" + queryName + "' declares too many columns (more than are mentioned in the Sql");
	    }
	
	} catch (SQLException e) {
	    throw new WdkModelException(e);
	}
    }
  

}

