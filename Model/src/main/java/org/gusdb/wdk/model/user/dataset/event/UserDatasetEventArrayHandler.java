package org.gusdb.wdk.model.user.dataset.event;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.fgputil.db.runner.SQLRunner;
import org.gusdb.fgputil.db.runner.SingleLongResultSetHandler;
import org.gusdb.fgputil.db.runner.SingleLongResultSetHandler.Status;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.config.ModelConfig;
import org.gusdb.wdk.model.config.ModelConfigParser;
import org.gusdb.wdk.model.config.ModelConfigUserDatasetStore;
import org.gusdb.wdk.model.user.dataset.UserDatasetDependency;
import org.gusdb.wdk.model.user.dataset.UserDatasetStore;
import org.gusdb.wdk.model.user.dataset.UserDatasetType;
import org.gusdb.wdk.model.user.dataset.UserDatasetTypeFactory;
import org.gusdb.wdk.model.user.dataset.UserDatasetTypeHandler;
import org.gusdb.wdk.model.user.dataset.event.UserDatasetExternalDatasetEvent.ExternalDatasetAction;
import org.gusdb.wdk.model.user.dataset.event.UserDatasetShareEvent.ShareAction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.SAXException;

public class UserDatasetEventArrayHandler {

  protected static final String ARG_PROJECT = "project";

  private UserDatasetStore userDatasetStore;
  private ModelConfig modelConfig;
  private String projectId;
  private String wdkTempDirName;

  private static final Logger logger = Logger.getLogger(UserDatasetEventArrayHandler.class);

  public void handleEventList(List<UserDatasetEvent> eventList,
      Map<UserDatasetType, UserDatasetTypeHandler> typeHandlers, Path tmpDir)
          throws WdkModelException {

    try (DatabaseInstance appDb = new DatabaseInstance(getModelConfig().getAppDB(), WdkModel.DB_INSTANCE_APP, true)) {

      DataSource appDbDataSource = appDb.getDataSource();
      Long lastHandledEventId = findLastHandledEvent(appDbDataSource, getUserDatasetSchemaName());
      int count = 0;

      for (UserDatasetEvent event : eventList) {

        if (lastHandledEventId != null && event.getEventId() <= lastHandledEventId) continue;

        if (event instanceof UserDatasetInstallEvent) {
          UserDatasetTypeHandler typeHandler = typeHandlers.get(event.getUserDatasetType());
          if (typeHandler == null)
            throw new WdkModelException("Install event " + event.getEventId() + " refers to typeHandler " +
                event.getUserDatasetType() + " which is not present in the wdk configuration");
          UserDatasetEventHandler.handleInstallEvent((UserDatasetInstallEvent) event, typeHandler,
              getUserDatasetStore(), appDbDataSource, getUserDatasetSchemaName(), tmpDir, getModelConfig().getProjectId());
        }

        else if (event instanceof UserDatasetUninstallEvent) {
          UserDatasetTypeHandler typeHandler = typeHandlers.get(event.getUserDatasetType());
          if (typeHandler == null)
            throw new WdkModelException("Uninstall event " + event.getEventId() + " refers to typeHandler " +
                event.getUserDatasetType() + " which is not present in the wdk configuration");
          UserDatasetEventHandler.handleUninstallEvent((UserDatasetUninstallEvent) event, typeHandler,
              appDbDataSource, getUserDatasetSchemaName(), tmpDir, getModelConfig().getProjectId());
        }

        else if (event instanceof UserDatasetShareEvent) {
          UserDatasetEventHandler.handleShareEvent((UserDatasetShareEvent) event,
              appDbDataSource, getUserDatasetSchemaName());
        }

        count++;
      }
      logger.info("Handled " + count + " new events");
    }
    catch (Exception e) {
      throw new WdkModelException(e);
    }
  }

  /**
   * Find the highest event id in the app db's handled events log.  Null if none.
   * 
   * @param appDbDataSource
   * @param userDatasetSchemaName
   * @return
   * @throws WdkModelException if the log has a failed event (no complete date) from a previous run.
   */
  private Long findLastHandledEvent(DataSource appDbDataSource, String userDatasetSchemaName) throws WdkModelException {

    SingleLongResultSetHandler handler = new SingleLongResultSetHandler();

    // first confirm there are no failed events from the last run.  (They'll have a null completed time)
    String sql = "select min(event_id) from " + userDatasetSchemaName + "UserDatasetEvent where completed is null";
    SQLRunner sqlRunner = new SQLRunner(appDbDataSource, sql, "find-earliest-incomplete-event-id");
    sqlRunner.executeQuery(handler);
    if (!handler.getStatus().equals(Status.NULL_VALUE)) {
      throw new WdkModelException("Event id " + handler.getRetrievedValue() + " failed to complete in a previous run");
    }

    // find highest previously handled event id
    sql = "select max(event_id) from " + userDatasetSchemaName + "UserDatasetEvent";
    sqlRunner = new SQLRunner(appDbDataSource, sql, "find-latest-event-id");
    sqlRunner.executeQuery(handler); 
    return handler.getRetrievedValue();
  }

  private UserDatasetStore getUserDatasetStore() throws WdkModelException {
    if (userDatasetStore == null) {
      ModelConfigUserDatasetStore udsConfig= getModelConfig().getUserDatasetStoreConfig();
      userDatasetStore = udsConfig.getUserDatasetStore();
    }
    return userDatasetStore;
  }

  /*
  public String getGusConfig(String key) throws IOException {
    if (gusProps == null) {
      String gusHome = System.getProperty("GUS_HOME");
      String configFileName = gusHome + "/config/gus.config";
      gusProps = new Properties();
      gusProps.load(new FileInputStream(configFileName));
    }
    String value = gusProps.getProperty(key);
    if (value == null)
      error("Required property " + key + " not found in gus.config file: " + configFileName);
    return value;
  }
  */

  // TODO: get from model config
  private String getUserDatasetSchemaName() {
    return  "ApiDBUserDatasets.";
  }

  public String getWdkTempDirName() throws WdkModelException {
    if (wdkTempDirName == null) {
      wdkTempDirName = getModelConfig().getWdkTempDir();
    }
    return wdkTempDirName;
  }

  private ModelConfig getModelConfig() throws  WdkModelException {
    if (modelConfig == null) {
      try {
        String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);
        ModelConfigParser parser = new ModelConfigParser(gusHome);
        modelConfig = parser.parseConfig(getProjectId());
      }
      catch (SAXException | IOException e) {
        throw new WdkModelException(e);
      }
    }
    return modelConfig;
  }

  /**
   * Accepts an array of event json objects from a Jenkins job that composes the content a series of
   * event files containing json objects into a single json array and returns a corresponding list
   * of UserDatasetEvent objects.
   * @param eventListing
   * @return
   * @throws WdkModelException
   */
  public static List<UserDatasetEvent> parseEventsArray(JSONArray eventJsonArray) throws WdkModelException {
    List<UserDatasetEvent> events = new ArrayList<UserDatasetEvent>();
    for(int i = 0; i < eventJsonArray.length(); i++) {
      JSONObject eventJson = eventJsonArray.getJSONObject(i);	
      parseEventObject(eventJson, events);
    }
    return events;
  }

  public static List<UserDatasetEvent> parseEventsFile(File eventsFile) throws WdkModelException {

    List<UserDatasetEvent> events = new ArrayList<UserDatasetEvent>();

    try (InputStream in = Files.newInputStream(eventsFile.toPath());
         BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {

      String line = null;
      while ((line = reader.readLine()) != null) {
        // TODO potentially content of this while loop can be replaced with parseEventLine method
        line = line.trim();
        if (line.length() == 0) break;
        if (line.startsWith("#")) continue;
        String[] columns = line.split("\t");

        Long eventId = new Long(columns[0]);
        String project = columns[2].length() > 0 ? columns[2] : null;
        Set<String> projectsFilter = new HashSet<String>();
        projectsFilter.add(project);
        Integer userDatasetId = new Integer(columns[3]);
        UserDatasetType userDatasetType = UserDatasetTypeFactory.getUserDatasetType(columns[4], columns[5]);

        // event_id install projects user_dataset_id ud_type_name ud_type_version owner_user_id genome genome_version
        if (columns[1].equals("install")) {
          Integer ownerUserId = new Integer(columns[6]);
          String[] dependencyArr = columns[7].split(" "); // for now, support just one dependency
          Set<UserDatasetDependency> dependencies = new HashSet<UserDatasetDependency>();
          dependencies.add(new UserDatasetDependency(dependencyArr[0], dependencyArr[1], ""));
          events.add(new UserDatasetInstallEvent(eventId, projectsFilter, userDatasetId, userDatasetType, ownerUserId, dependencies));
        }

        // event_id uninstall projects user_dataset_id ud_type_name ud_type_version
        else if (columns[1].equals("uninstall")) {
          events.add(new UserDatasetUninstallEvent(eventId, projectsFilter, userDatasetId, userDatasetType));
        }

        // event_id share projects user_dataset_id ud_type_name ud_type_version owner_id recipient_id grant
        else if (columns[1].equals("share")) {
          Integer ownerId = new Integer(columns[6]);
          Integer recipientId = new Integer(columns[7]);
          ShareAction action = columns[8].equals("grant") ?
              ShareAction.GRANT : ShareAction.REVOKE;
          events.add(new UserDatasetShareEvent(eventId, projectsFilter, userDatasetId, userDatasetType, ownerId, recipientId, action));
        }

        else {
          throw new WdkModelException("Unrecognized user dataset event type: " + columns[1]);
        }
      }
    }
    catch (IOException e) {
      throw new WdkModelException(e);
    }

    return events;
  }

  private static boolean parseEventObject(JSONObject eventJson, List<UserDatasetEvent> events) throws WdkModelException {
    Long eventId = eventJson.getLong("eventId");
    String event = eventJson.getString("event");
    String projectsJson = eventJson.getJSONArray("projects").toString();
    ObjectMapper mapper = new ObjectMapper();
    TypeReference<Set<String>> setType = new TypeReference<Set<String>>() {};
    Set<String> projects;
	try {
	  projects = mapper.readValue(projectsJson, setType);
	} 
	catch(IOException ioe) {
	  throw new WdkModelException(ioe);
	}
    Set<String> projectsFilter = new HashSet<>();
    projectsFilter.addAll(projects);
    Integer userDatasetId = eventJson.getInt("datasetId");
    TypeReference<Map<String,String>> mapType  = new TypeReference<Map<String,String>>() {};
    String typeJson = eventJson.getJSONObject("type").toString();
    Map<String, String> type = null;
    try {
      type = mapper.readValue(typeJson, mapType);
    }
    catch(IOException ioe) {
      throw new WdkModelException(ioe);
    }
    UserDatasetType userDatasetType = UserDatasetTypeFactory.getUserDatasetType(type.get("name"), type.get("version"));

    // event_id install projects user_dataset_id ud_type_name ud_type_version owner_user_id genome genome_version
    if ("install".equals(event)) {
      Integer ownerUserId = eventJson.getInt("owner");
      Set<UserDatasetDependency> dependencies = new HashSet<UserDatasetDependency>();
      JSONArray dependencyJsonArray = eventJson.getJSONArray("dependencies");
      for(int i = 0; i < dependencyJsonArray.length(); i++) {
        JSONObject dependencyJson = dependencyJsonArray.getJSONObject(i);
        dependencies.add(new UserDatasetDependency(dependencyJson.getString("resourceIdentifier"),
        		dependencyJson.getString("resourceVersion"), dependencyJson.getString("resourceDisplayName")));
      }
      events.add(new UserDatasetInstallEvent(eventId, projectsFilter, userDatasetId, userDatasetType, ownerUserId, dependencies));
    }

    // event_id uninstall projects user_dataset_id ud_type_name ud_type_version
    else if ("uninstall".equals(event)) {
      events.add(new UserDatasetUninstallEvent(eventId, projectsFilter, userDatasetId, userDatasetType));
    }

    // event_id share projects user_dataset_id ud_type_name ud_type_version owner_id recipient_id grant
    else if ("share".equals(event)) {
      Integer ownerId = eventJson.getInt("owner");
      Integer recipientId = eventJson.getInt("recipient");
      ShareAction action = "grant".equals(eventJson.getString("action")) ?
          ShareAction.GRANT : ShareAction.REVOKE;
      events.add(new UserDatasetShareEvent(eventId, projectsFilter, userDatasetId, userDatasetType, ownerId, recipientId, action));
    }

    else {
      throw new WdkModelException("Unrecognized user dataset event type: " + event);
    }
    return false;
  }

  public void setProjectId(String projectId) {this.projectId = projectId;}
  private String getProjectId() { return projectId;}

 
}