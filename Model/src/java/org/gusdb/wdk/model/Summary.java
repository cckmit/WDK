package org.gusdb.wdk.model;

import java.util.Map;

/**
 * Summary.java
 *
 * A class representing a binding between a RecordClass and a Query.
 *
 * Created: Fri June 4 11:19:30 2004 EDT
 *
 * @author David Barkan
 * @version $Revision$ $Date$ $Author$
 */

public class Summary {

    private String recordClassTwoPartName;

    private String queryTwoPartName;

    private String name;

    private SummarySet summarySet;


    //the only column in this query should be a primary key
    Query query;
    
    //QueryInstance to be shared across all SummaryInstances 
    //produced by this Summary
    QueryInstance listIdQueryInstance;

    RecordClass recordClass;

    public Summary(){
    }
    
    public SummaryInstance makeSummaryInstance(Map paramValues, int i, int j) throws WdkUserException, WdkModelException{
	
	if (listIdQueryInstance == null){
	    listIdQueryInstance = query.makeInstance();
	}
	//return new SummaryInstance(this, listIdQueryInstance);
	SummaryInstance summaryInstance = 
	    new SummaryInstance(this, query.makeInstance(), paramValues, i, j);
	return summaryInstance;
    }

    public Query getQuery(){
	return this.query;
    }

    public Param[] getParams() {
	return query.getParams();
    }

    public String getDisplayName() {
	return query.getDisplayName();
    }
	
    public String getHelp() {
	return query.getHelp();
    }

    public String getDescription() {
	return query.getDescription();
    }

    public RecordClass getRecordClass(){
	return this.recordClass;
    }

    public void setRecordClassRef(String recordClassTwoPartName){

	this.recordClassTwoPartName = recordClassTwoPartName;
    }

    public void setQueryRef(String queryTwoPartName){

	this.queryTwoPartName = queryTwoPartName;
    }

    public void resolveReferences(WdkModel model)throws WdkModelException{
	
	this.query = (Query)model.resolveReference(queryTwoPartName, name, "question", "queryRef");
	this.recordClass = (RecordClass)model.resolveReference(recordClassTwoPartName, name, "question", "recordClassRef");
    }

    public String getName(){
	return name;
    }

    public void setName(String name){
	this.name = name;
    }

    public String getFullName() {
	return summarySet.getName() + "." + name;
    }

    public int getTotalLength(Map values) throws WdkModelException, WdkUserException{
        SummaryInstance si = makeSummaryInstance(values, 0, 0);
        return si.getTotalLength();
    }
    //set dummy values for start and end because they will not be used.
    //(might have to change this depending on resolution of efficiency issue)

    public String toString() {
	String newline = System.getProperty( "line.separator" );
	StringBuffer buf =
	    new StringBuffer("Question: name='" + name + "'" + newline  +
			     "  recordClass='" + recordClassTwoPartName + "'" + newline +
			     "  query='" + queryTwoPartName + "'" + newline
			     );	    
	return buf.toString();
    }
    
    protected void setSummarySet(SummarySet summarySet) {
	this.summarySet = summarySet;
    }

}
