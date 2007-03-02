package org.gusdb.wdk.model;

public abstract class Param {
    
    protected String name;
    protected String id;
    protected String prompt;
    protected String help;
    protected String defaultValue;
    protected String fullName;
    
    private boolean visible;
    private boolean readonly;
    
    private Group group;
    
    public Param( ) {
        visible = true;
        readonly = false;
        group = Group.Empty();
    }
    
    public void setName( String name ) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public String getFullName() {
        return fullName;
    }
    
    public void setId( String id ) {
        this.id = id;
    }
    
    public String getId() {
        return id;
    }
    
    /**
     * Assumes that the name of this param has already been set. Note this is
     * slightly different than a simple accessor in that the full name of the
     * param is <code>paramSetName</code> concatenated with ".paramName".
     * 
     * @param paramSetName
     *            name of the paramSet to which this param belongs.
     */
    public void setFullName( String paramSetName ) {
        this.fullName = paramSetName + "." + name;
    }
    
    public void setPrompt( String prompt ) {
        this.prompt = prompt;
    }
    
    public String getPrompt() {
        if ( prompt == null ) return name;
        return prompt;
    }
    
    public void setHelp( String help ) {
        this.help = help;
    }
    
    public String getHelp() {
        if ( help == null ) return getPrompt();
        return help;
    }
    
    public void setDefault( String defaultValue ) {
        if ( defaultValue == null ) return; // use the current one
        this.defaultValue = defaultValue;
    }
    
    public String getDefault() {
        return defaultValue;
    }
    
    /**
     * @return Returns the readonly.
     */
    public boolean isReadonly() {
        return this.readonly;
    }
    
    /**
     * @param readonly
     *            The readonly to set.
     */
    public void setReadonly( boolean readonly ) {
        this.readonly = readonly;
    }
    
    /**
     * @return Returns the visible.
     */
    public boolean isVisible() {
        return this.visible;
    }
    
    /**
     * @param visible
     *            The visible to set.
     */
    public void setVisible( boolean visible ) {
        this.visible = visible;
    }
    
    /**
     * @return the group
     */
    public Group getGroup() {
        return group;
    }
    
    /**
     * @param group
     *            the group to set
     */
    public void setGroup( Group group ) {
        this.group = group;
    }
    
    public String toString() {
        String newline = System.getProperty( "line.separator" );
        String classnm = this.getClass().getName();
        StringBuffer buf = new StringBuffer( classnm + ": name='" + name + "'"
                + ": id='" + id + "'" + newline + "  prompt='" + prompt + "'"
                + newline + "  help='" + help + "'" + newline + "  default='"
                + defaultValue + "'" + newline + "  readonly=" + readonly
                + newline + "  visible=" + visible + newline );
        if ( group != null )
            buf.append( "  group=" + group.getName() + newline );
        
        return buf.toString();
    }
    
    /**
     * @return Error string if an error. null if no errors.
     */
    public abstract String validateValue( Object value )
            throws WdkModelException;
    
    // ////////////////////////////////////////////////////////////////////
    // protected methods
    // ////////////////////////////////////////////////////////////////////
    
    /**
     * Transforms external value into internal value if needed By default
     * returns provided value
     */
    protected String getInternalValue( String value ) throws WdkModelException {
        // check if the param is StringParam, then replace the escape character
        if ( this instanceof StringParam )
            return value.replaceAll( "'", "''" );
        return value;
    }
    
    protected abstract void resolveReferences( WdkModel model )
            throws WdkModelException;
    
    protected void setResources( WdkModel model ) throws WdkModelException {}
    
    protected void clone( Param param ) {
        param.name = name;
        param.id = id;
        param.prompt = prompt;
        param.help = help;
        param.defaultValue = defaultValue;
        param.fullName = fullName;
        param.visible = visible;
        param.readonly = readonly;
    }
    
    public abstract Param clone();
}
