/**
 * @description
 */
package org.gusdb.wdk.model.migrate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.json.JSONException;
import org.xml.sax.SAXException;

/**
 * @author Jerric
 * @created May 22, 2007
 * @modified May 22, 2007
 */
public class Migrator {

    /**
     * Before running this command, the preparation steps are expected to be
     * performed, such as setting up the new table schema, running the SQL
     * migration script.
     * 
     * The arguments for the command are:
     * 
     * -model <model_name>: the name of the model to be used
     * 
     * -version <old_ver>: the version of the model to be migrated from; the
     * version is used to determine which migration code to be executed;
     * 
     * -schema <old_user_schema>: the old user login schema, where the user data
     * is migrated from
     * 
     * @param args
     * @throws WdkModelException
     * @throws WdkUserException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws JSONException
     * @throws SQLException
     * @throws SAXException
     * @throws IOException
     * @throws TransformerException
     * @throws TransformerFactoryConfigurationError
     * @throws ParserConfigurationException
     * @throws NoSuchAlgorithmException
     */
    public static void main(String[] args) throws WdkModelException,
            WdkUserException, NoSuchAlgorithmException,
            ParserConfigurationException, TransformerFactoryConfigurationError,
            TransformerException, IOException, SAXException, SQLException,
            JSONException, InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        // determine which version of the migrator to be used
        Migrator migrator = new Migrator();
        migrator.parseOptions(args);
        String oldVersion = migrator.getOldVersion();
        String newVersion = migrator.getNewVersion();
        oldVersion = oldVersion.replace('.', '_');
        newVersion = newVersion.replace('.', '_');

        // construct the migrator class to do the real work
        String className = Migrator.class.getName() + oldVersion + "To"
                + newVersion;
        Class<?> migratorClass = Class.forName(className);
        migrator = (Migrator) migratorClass.newInstance();
        migrator.parseOptions(args);
        migrator.migrate();

        System.out.println("Migration from " + oldVersion + " to " + newVersion
                + " is finished");
    }

    private String commandName;

    protected String helpHeader;
    protected String helpFooter;
    protected Options options;

    protected String oldVersion;
    protected String oldSchema;
    protected WdkModel wdkModel;

    public Migrator() {
        options = new Options();
        helpHeader = "Migrate user data from previous release into current release";
        helpFooter = "";
        declareOptions();
    }

    public void parseOptions(String[] args) throws WdkModelException,
            NoSuchAlgorithmException, ParserConfigurationException,
            TransformerFactoryConfigurationError, TransformerException,
            IOException, SAXException, SQLException, JSONException,
            WdkUserException, InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        commandName = System.getProperty("cmdName", "migrate");

        CommandLineParser parser = new BasicParser();
        String modelName = null;
        try {
            // parse the command line arguments
            CommandLine commandLine = parser.parse(options, args);
            modelName = commandLine.getOptionValue("model");
            oldVersion = commandLine.getOptionValue("version");
            oldSchema = commandLine.getOptionValue("schema");
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("");
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            System.err.println("");
            printUsage();
        }

        // parse the wdk model
        String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);
        wdkModel = WdkModel.construct(modelName, gusHome);
    }

    public String getOldVersion() {
        return oldVersion;
    }

    public String getNewVersion() {
        return WdkModel.WDK_VERSION;
    }

    public String getOldSchema() {
        return oldSchema;
    }

    public String getNewSchema() throws WdkUserException {
        return wdkModel.getUserFactory().getLoginSchema();
    }

    public WdkModel getWdkModel() {
        return wdkModel;
    }

    protected void declareOptions() {
        Option option = new Option("model", true,
                "the name of the model.  This is used to find the Model XML "
                        + "file ($GUS_HOME/config/model_name.xml) the Model "
                        + "property file ($GUS_HOME/config/model_name.prop) "
                        + "and the Model config file "
                        + "($GUS_HOME/config/model_name-config.xml)");
        option.setRequired(true);
        option.setArgName("model");
        options.addOption(option);

        option = new Option("version", true,
                "the version of the model to be migrated from; the version is "
                        + "used to determine which migration code to be "
                        + "executed");
        option.setRequired(true);
        option.setArgName("version");
        options.addOption(option);

        option = new Option("schema", true,
                "the old user login schema, where the user data is migrated "
                        + "from");
        option.setRequired(true);
        option.setArgName("schema");
        options.addOption(option);
    }

    protected void printUsage() {
        String newline = System.getProperty("line.separator");

        // print command syntax
        StringBuffer syntax = new StringBuffer(commandName);
        for (Object object : options.getOptions()) {
            Option option = (Option) object;
            syntax.append(option.isRequired() ? " -" : " [-");
            syntax.append(option.getArgName());
            if (option.hasArg()) {
                syntax.append(" <" + option.getArgName());
                syntax.append(option.hasArgs() ? " ...>" : ">");
            }
            if (!option.isRequired()) syntax.append(']');
        }
        syntax.append(newline);

        // PrintWriter stderr = new PrintWriter(System.err);
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(75, syntax.toString(), helpHeader, options,
                helpFooter);
        System.exit(1);
    }

    /**
     * start migration. This method will be overridden by sub-classes
     * 
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     * @throws JSONException 
     */
    public void migrate() throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {
        throw new UnsupportedOperationException("The subclass must implement"
                + " this method.");
    }
}
