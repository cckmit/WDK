package org.gusdb.wdk.model.query.param;

public class OntologyItem {
  private String ontologyId;
  private String parentOntologyId;
  private String displayName;
  private String description;
  private String type;
  private String units;
  private String precision;

  public void setOntologyId(String ontologyId) {
    this.ontologyId = ontologyId;
  }

  public void setParentOntologyId(String parentOntologyId) {
    this.parentOntologyId = parentOntologyId;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setUnits(String units) {
    this.units = units;
  }

  public void setPrecision(String precision) {
    this.precision = precision;
  }

  public String getOntologyId() {
    return ontologyId;
  }

  public String getParentOntologyId() {
    return parentOntologyId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public String getType() {
    return type;
  }

  public String getUnits() {
    return units;
  }

  public String getPrecision() {
    return precision;
  }

}