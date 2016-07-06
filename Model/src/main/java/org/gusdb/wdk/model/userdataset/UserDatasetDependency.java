package org.gusdb.wdk.model.userdataset;

/**
 * Describes a dependency of a user dataset on an application resource (such as
 * data in the application database).
 * @author steve
 *
 */
public interface UserDatasetDependency {
  /**
   * The identifier of the resource depended on
   * @return
   */
  String getResourceIdentifier();
  
  /**
   * The version of the resource depended on
   * @return
   */
  String getResourceVersion();
  
  /**
   * A human readable form of the resource, for display to end user
   * @return
   */
  String getDisplayable();
}
