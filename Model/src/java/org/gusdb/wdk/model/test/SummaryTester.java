package org.gusdb.wdk.model.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.gusdb.wdk.model.Answer;
import org.gusdb.wdk.model.Question;
import org.gusdb.wdk.model.QuestionSet;
import org.gusdb.wdk.model.RecordClass;
import org.gusdb.wdk.model.RecordInstance;
import org.gusdb.wdk.model.Reference;
import org.gusdb.wdk.model.SummaryTable;
import org.gusdb.wdk.model.SummaryView;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.report.Reporter;
import org.json.JSONException;

public class SummaryTester {

    public static void main(String[] args) {

        String cmdName = System.getProperty("cmdName");

        // process args
        Options options = declareOptions();
        CommandLine cmdLine = parseOptions(cmdName, options, args);

        String questionFullName = cmdLine.getOptionValue("question");

        String[] params = null;
        boolean haveParams = cmdLine.hasOption("params");
        if (haveParams) params = cmdLine.getOptionValues("params");

        boolean toXml = cmdLine.hasOption("toXml");
        String xmlFileName = cmdLine.getOptionValue("toXml");
        String[] rows = cmdLine.getOptionValues("rows");

        boolean hasFormat = cmdLine.hasOption("format");
        String format = cmdLine.getOptionValue("format");

        boolean hasFormatConfig = cmdLine.hasOption("config");
        String configFile = cmdLine.getOptionValue("config");

        if (!hasFormat && hasFormatConfig) {
            throw new IllegalArgumentException(
                    "Please specify the output format before providing the configuration file for that format.");
        }

        if (toXml) {
            if (xmlFileName == null || xmlFileName.equals(""))
                usage(cmdName, options);
        } else {
            if (rows == null || rows.length == 0) usage(cmdName, options);
            validateRowCount(rows);
        }

        // get subType, if any
        String[] viewValues = null;
        if (cmdLine.hasOption("view"))
            viewValues = cmdLine.getOptionValues("view");

        try {
            // variable never used
            Reference ref = new Reference(questionFullName);
            String questionSetName = ref.getSetName();
            String questionName = ref.getElementName();
            WdkModel wdkModel = WdkModel.construct(cmdLine.getOptionValue("model"));

            QuestionSet questionSet = wdkModel.getQuestionSet(questionSetName);
            Question question = questionSet.getQuestion(questionName);

            Map<String, Object> paramValues = new LinkedHashMap<String, Object>();
            if (haveParams) {
                paramValues = parseParamArgs(params);
            }

            // parse the summary view
            SummaryView view = parseSummaryView(question, viewValues);

            // this is suspicious
            // Query query = question.getQuery();
            // query.setIsCacheable(new Boolean(true));
            int pageCount = 1;

            if (toXml) {
                writeSummaryAsXml(question, paramValues, xmlFileName, view);
                return;
            }

            for (int i = 0; i < rows.length; i += 2) {
                int nextStartRow = Integer.parseInt(rows[i]);
                int nextEndRow = Integer.parseInt(rows[i + 1]);

                Answer answer = question.makeAnswer(paramValues, nextStartRow,
                        nextEndRow, view);

                // this is wrong. it only shows one attribute query, not
                // all. Fix this in Answer by saving a list of attribute
                // queries, not just one.
                if (cmdLine.hasOption("showQuery")) {
                    System.out.println(getLowLevelQuery(answer));
                }

                if (rows.length != 2) System.out.println("page " + pageCount);

                // print the size of the answer
                System.out.println("Total # of records: "
                        + answer.getResultSize());

                // load configuration for output format
                if (!hasFormat) format = "tabular";
                Map<String, String> config = loadConfiguration(configFile);

                Reporter reporter = answer.createReport(format, config,
                        nextStartRow, nextEndRow);

                reporter.write(System.out);
                System.out.println();

                pageCount++;
            }
        } catch (WdkUserException e) {
            System.err.println(e.formatErrors());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            // System.exit(1);
        }
        // System.exit(0);

        // HACK
        // prevent the app from exiting
        // while(true){}
    }

    private static SummaryView parseSummaryView(Question question,
            String[] viewValues) throws WdkModelException {
        if (viewValues == null || viewValues.length != 3) return null;
        RecordClass recordClass = question.getRecordClass();
        SummaryTable summaryTable = recordClass.getSummaryTable(viewValues[0]);
        SummaryView view = summaryTable.getView(viewValues[1], viewValues[2]);
        return view;
    }

    private static Map<String, String> loadConfiguration(String configFileName)
            throws IOException {
        Map<String, String> config = new LinkedHashMap<String, String>();

        if (configFileName == null || configFileName.length() == 0)
            return config;

        InputStream stream = new FileInputStream(configFileName);
        Properties properties = new Properties();
        if (configFileName.toLowerCase().endsWith(".xml")) {
            properties.loadFromXML(stream);
        } else properties.load(stream);
        stream.close();

        for (Object obj : properties.keySet()) {
            String key = (String) obj;
            config.put(key, properties.getProperty(key));
        }
        return config;
    }

    private static void writeSummaryAsXml(Question question,
            Map<String, Object> paramValues, String xmlFile, SummaryView view)
            throws WdkModelException, WdkUserException, IOException,
            NoSuchAlgorithmException, SQLException, JSONException {

        Answer answer = question.makeAnswer(paramValues, 1, 10, view);
        int resultSize = answer.getResultSize();
        answer = question.makeAnswer(paramValues, 1, resultSize, view);
        FileWriter fw = new FileWriter(new File(xmlFile), false);

        String newline = System.getProperty("line.separator");
        String ident = "    ";

        fw.write("<" + question.getFullName() + ">" + newline);
        fw.close();
        fw = new FileWriter(new File(xmlFile), true);
        for (RecordInstance ri : answer.getRecordInstances()) {
            fw.write(ri.toXML(ident) + newline);
        }
        fw.write("</" + question.getFullName() + ">" + newline);
        fw.close();
    }

    private static String getLowLevelQuery(Answer answer)
            throws WdkModelException, NoSuchAlgorithmException, SQLException,
            JSONException, WdkUserException {
        // QueryInstance instance = answer.getAttributesQueryInstance();
        QueryInstance instance = answer.getIdsQueryInstance();
        String query = instance.getSql();
        String newline = System.getProperty("line.separator");
        String newlineQuery = query.replaceAll("^\\s\\s\\s", newline);
        newlineQuery = newlineQuery.replaceAll("(\\S)\\s\\s\\s", "$1" + newline);
        return newline + newlineQuery + newline;
    }

    private static void addOption(Options options, String argName, String desc) {

        Option option = new Option(argName, true, desc);
        option.setRequired(true);
        option.setArgName(argName);

        options.addOption(option);
    }

    static Options declareOptions() {
        Options options = new Options();

        // model name
        addOption(options, "model", "the name of the model.  This is used to "
                + "find the Model XML file ($GUS_HOME/config/model_name.xml) "
                + "the Model property file ($GUS_HOME/config/model_name.prop) "
                + "and the Model config file "
                + "($GUS_HOME/config/model_name-config.xml)");

        // question name
        addOption(options, "question", "The full name (set.element) of the "
                + "question to run.");

        // rows to return
        Option rows = new Option("rows", "The start and end pairs of the "
                + "summary rows to return. Ignored when toXml is turned on, "
                + "but required otherwise.");
        rows.setArgs(2);
        options.addOption(rows);

        // show query
        Option showQuery = new Option("showQuery", "Show the query as it will "
                + "be run (with parameter values in place).");
        options.addOption(showQuery);

        // output XML
        Option toXml = new Option("toXml", true, "output summary in XML format"
                + " to given file");
        options.addOption(toXml);

        // output XML
        Option fullRecords = new Option("fullRecords", "output full records");
        options.addOption(fullRecords);

        // output format
        Option format = new Option("format", true, "the output format, which "
                + "is record type specific (defined in the model file)");
        options.addOption(format);

        // the config file for output format
        Option config = new Option("config", true, "The configuration file "
                + "for the output format");
        options.addOption(config);

        // the sub type input
        Option view = new Option("view", true, "The summary view to be used "
                + "to filter (or not filter the result");
        view.setArgName("view");
        view.setArgs(3);
        options.addOption(view);

        // params
        Option params = new Option("params", true,
                "Space delimited list of param_name param_value ....");
        params.setArgName("params");
        params.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(params);
        return options;
    }

    static CommandLine parseOptions(String cmdName, Options options,
            String[] args) {

        CommandLineParser parser = new BasicParser();
        CommandLine cmdLine = null;
        try {
            // parse the command line arguments
            cmdLine = parser.parse(options, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("");
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            System.err.println("");
            usage(cmdName, options);
        }

        return cmdLine;
    }

    static void validateRowCount(String[] rows) {
        if (rows.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "The -rows option must be followed by pairs of row numbers (each pair representing the start and end of a page");
        }
    }

    static Map<String, Object> parseParamArgs(String[] params) {

        Map<String, Object> h = new LinkedHashMap<String, Object>();
        if (params[0].equals("NONE")) {
            return h;
        } else {
            if (params.length % 2 != 0) {
                throw new IllegalArgumentException(
                        "The -params option must be followed by key value pairs only");
            }
            for (int i = 0; i < params.length; i += 2) {
                h.put(params[i], params[i + 1]);
            }
            return h;
        }
    }

    static void usage(String cmdName, Options options) {

        String newline = System.getProperty("line.separator");
        String cmdlineSyntax = cmdName
                + " -model model_name\n"
                + " -question full_question_name\n"
                + " [-rows start end]\n"
                + " [-showQuery]\n"
                + " [-toXml <xmlFile>|-fullRecords]\n"
                + " [-format tabular | gff3 | fullRecords [-config <config_file>]]\n"
                + " [-view <summary_table row_term column_term>]\n"
                + " -params param_1_name param_1_value ...\n";

        String header = newline
                + "Print a summary found in a WDK Model xml file. Options:";

        String footer = "";

        // PrintWriter stderr = new PrintWriter(System.err);
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(75, cmdlineSyntax, header, options, footer);
        System.exit(1);
    }

}
