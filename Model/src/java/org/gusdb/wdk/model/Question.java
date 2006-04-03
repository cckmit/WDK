package org.gusdb.wdk.model;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * Question.java
 *
 * A class representing a binding between a RecordClass and a Query.
 *
 * Created: Fri June 4 11:19:30 2004 EDT
 *
 * @author David Barkan
 * @version $Revision$ $Date$ $Author$
 */

public class Question implements Serializable {

    private static final long serialVersionUID = -446811404645317117L;
    private Logger logger = Logger.getLogger(Question.class);

    private String recordClassTwoPartName;

    private String queryTwoPartName;

    private String name;

    private String displayName;

    private String description;

    private String help;

    private QuestionSet questionSet;

    private Query query;
    
    protected RecordClass recordClass;

    private String category;

    private String[] summaryAttributeNames;

    private Map<String, AttributeField> summaryAttributeMap;

    private DynamicAttributeSet dynamicAttributes;

    ///////////////////////////////////////////////////////////////////////
    // setters called at initialization
    ///////////////////////////////////////////////////////////////////////
    
    public Question(){
	summaryAttributeMap = new LinkedHashMap<String, AttributeField>();
    }


    public void setName(String name){
	this.name = name;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public void setHelp(String help) {
	this.help = help;
    }

    public void setRecordClassRef(String recordClassTwoPartName){

	this.recordClassTwoPartName = recordClassTwoPartName;
    }

    public void setQueryRef(String queryTwoPartName){
	this.queryTwoPartName = queryTwoPartName;
    }

    public void setDisplayName(String displayName) {
	this.displayName = displayName;
    }

    public void setCategory(String category) {
	this.category = category;
    }

    public void setSummaryAttributesList(String summaryAttributesString){
       	// ensure that the list includes primaryKey
	String primaryKey = RecordClass.PRIMARY_KEY_NAME;
	if (!(summaryAttributesString.equals(primaryKey)
	      || summaryAttributesString.startsWith(primaryKey + ",")
	      || summaryAttributesString.endsWith("," + primaryKey)
	      || summaryAttributesString.indexOf("," + primaryKey + ",") > 0)) {
	    summaryAttributesString = primaryKey + "," + summaryAttributesString;
	}
	this.summaryAttributeNames = summaryAttributesString.split(",\\s*");
    }

    public void setDynamicAttributeSet(DynamicAttributeSet dynamicAttributes) {
	this.dynamicAttributes = dynamicAttributes;
    }

    public Map<String, AttributeField> getSummaryAttributes(){
	return summaryAttributeMap;
    }

    public Map<String, AttributeField> getReportMakerAttributeFields() {
        Map<String, AttributeField> rmfields = recordClass.getReportMakerAttributeFieldMap();
        if (dynamicAttributes != null)
            rmfields.putAll(dynamicAttributes.getReportMakerAttributeFieldMap());
        return rmfields;
    }

    ///////////////////////////////////////////////////////////////////////


    public Answer makeAnswer(Map<String, Object> paramValues, int i, int j)
            throws WdkUserException, WdkModelException {
        QueryInstance qi = query.makeInstance();
        qi.setValues(paramValues);
        Answer answer = new Answer(this, qi, i, j);

        return answer;
    }

    public Param[] getParams() {
	return query.getParams();
    }
    
    public Map<String, Param> getParamMap() {
        return query.getParamMap();
    }

    public String getDescription() {
	return description;
    }

    public String getHelp() {
	return help;
    }

    public String getDisplayName() {
    if (displayName == null) displayName = getFullName();
	return displayName;
    }
	
    public String getCategory() {
	return category;
    }

    public RecordClass getRecordClass(){
	return this.recordClass;
    }

    Query getQuery(){
	return this.query;
    }

    public void setRecordClass(RecordClass rc){
	this.recordClass = rc;
    }
    
    public void setQuery(Query q){
	this.query = q;
    }

    public String getName(){
	return name;
    }

    public String getFullName() {
    if (questionSet == null) return name;
    else return questionSet.getName() + "." + name;
    }

    public String toString() {
	String newline = System.getProperty( "line.separator" );

	StringBuffer saNames = new StringBuffer();
	if (summaryAttributeNames != null) {
	    for (String saName : summaryAttributeNames) 
		saNames.append(saName + ", ");
	}
	StringBuffer buf =
	    new StringBuffer("Question: name='" + name + "'" + newline  +
			     "  recordClass='" + recordClassTwoPartName + "'" + newline +
			     "  query='" + queryTwoPartName + "'" + newline +
			    "  displayName='" + getDisplayName() + "'" + newline +
			    "  description='" + getDescription() + "'" + newline +
			    "  summaryAttributes='" + saNames + "'" + newline + 
			    "  help='" + getHelp() + "'" + newline 
			     );	    
	if (dynamicAttributes != null) {
	    buf.append(dynamicAttributes.toString());
	}
	return buf.toString();
    }

    public boolean isDynamic() {
	return dynamicAttributes != null;
    }
    
    ///////////////////////////////////////////////////////////////////////
    // package methods
    ///////////////////////////////////////////////////////////////////////

    Map<String, AttributeField> getDynamicAttributeFields() {
	return dynamicAttributes == null ? null: dynamicAttributes.getAttributeFields();
    }

    void setResources(WdkModel model) throws WdkModelException {
	if (dynamicAttributes != null) dynamicAttributes.setResources(model);
    }

    Map<String, AttributeField> getAttributeFields() {
        Map<String, AttributeField> attributeFields = new LinkedHashMap<String, AttributeField>(
                recordClass.getAttributeFieldMap());
        if (dynamicAttributes != null) {
            attributeFields.putAll(dynamicAttributes.getAttributeFields());
        }
        return attributeFields;
    }
    
    void resolveReferences(WdkModel model)throws WdkModelException{

	this.query = (Query)model.resolveReference(queryTwoPartName, name, 
						   "question", "queryRef");
	Object rc = model.resolveReference(recordClassTwoPartName, name, 
					   "question", "recordClassRef");
       	setRecordClass((RecordClass)rc);
    }

    boolean isSummaryAttribute(String attName){
	return summaryAttributeMap.get(attName) != null;
    }
    
    void setSummaryAttributesMap(Map<String, AttributeField> summaryAtts){
	this.summaryAttributeMap = summaryAtts;
    }

    ///////////////////////////////////////////////////////////////////////
    // Protected Methods
    ///////////////////////////////////////////////////////////////////////
        
    protected void setQuestionSet(QuestionSet questionSet) throws WdkModelException {
	this.questionSet = questionSet;
	if (dynamicAttributes != null) {
	    dynamicAttributes.setQuestion(this);
	}
	initSummaryAttributes();
    }

    private void initSummaryAttributes() throws WdkModelException {
        if (summaryAttributeNames != null) {
            summaryAttributeMap = new LinkedHashMap<String, AttributeField>();
            Map<String, AttributeField> attMap = getAttributeFields();

            for (String name : summaryAttributeNames) {
                if (attMap.get(name) == null) {
                    throw new WdkModelException("Question " + getName()
                            + " has unknown summary attribute: '" + name + "'");
                }
                summaryAttributeMap.put(name, attMap.get(name));
            }
        } else {
            Map<String, AttributeField> recAttrsMap = getRecordClass().getAttributeFieldMap();
            summaryAttributeMap = new LinkedHashMap<String, AttributeField>(
                    recAttrsMap);
            Iterator<String> ramI = recAttrsMap.keySet().iterator();
            String attribName = null;
            while (ramI.hasNext()) {
                attribName = ramI.next();
                AttributeField attr = recAttrsMap.get(attribName);
                if (attr.getInternal()) {
                    summaryAttributeMap.remove(attribName);
                }
            }
        }
    }
    
    /**
     * This method is use to clone the question, excluding dynamic attributes
     * @return
     */
    public Question getBaseQuestion() {
        Question question = new Question();
        question.description = this.description;
        question.displayName = this.displayName;
        question.help = this.help;
        question.name = this.name;
        question.queryTwoPartName = this.queryTwoPartName;
        question.questionSet = this.questionSet;
        question.recordClass = this.recordClass;
        question.recordClassTwoPartName = this.recordClassTwoPartName;

        // needs to clone thie summary attribute as well
        Map<String, AttributeField> sumAttributes = 
            new LinkedHashMap<String, AttributeField>();
        Map<String, AttributeField> attributes = recordClass.getAttributeFieldMap();
        for (String attrName : summaryAttributeMap.keySet()) {
            if (attributes.containsKey(attrName))
                sumAttributes.put(attrName, summaryAttributeMap.get(attrName));
        }
        question.summaryAttributeMap = sumAttributes;

        // clone the query too, but excludes the columns in dynamic attribute
        Set<String> excludedColumns= new LinkedHashSet<String>();
        // TEST
        StringBuffer sb = new StringBuffer();
        
        if (this.dynamicAttributes!= null) {
            attributes = dynamicAttributes.getAttributeFields();
            for (AttributeField field : attributes.values()) {
                if (field instanceof ColumnAttributeField) {
                    ColumnAttributeField cfield = (ColumnAttributeField) field;
                    excludedColumns.add(cfield.getColumn().getName());
                    // TEST
                    sb.append(cfield.getColumn().getName() + ", ");
                }
            }
        }
        logger.debug("Excluded fields: " + sb.toString());
        
        Query newQuery = this.query.getBaseQuery(excludedColumns);
        question.query = newQuery;
        
        return question;
    }
}
