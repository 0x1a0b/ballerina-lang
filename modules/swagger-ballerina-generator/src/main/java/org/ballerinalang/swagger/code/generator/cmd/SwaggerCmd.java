/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.swagger.code.generator.cmd;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.swagger.codegen.CodegenConstants;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ballerinalang.launcher.BLauncherCmd;
import org.ballerinalang.launcher.LauncherUtils;

import java.io.PrintStream;
import java.util.List;

/**
 * Class to implement "swagger" command for ballerina.
 * Ex: ballerina swagger (connector | skeleton | mock) (swaggerFile) -p(package name) -d(output directory name)
 */
@Parameters(commandNames = "swagger", commandDescription = "Generate connector/service using swagger definition")
public class SwaggerCmd implements BLauncherCmd {
    private final String CONNECTOR = "connector";
    private final String SKELETON = "skeleton";
    private final String MOCK = "mock";

    private static final PrintStream outStream = System.err;

    private JCommander parentCmdParser;

    @Parameter(arity = 1, description = "<action> <swagger specification>. action : connector|skeleton|mock")
    private List<String> argList;

    @Parameter(names = {"-d", "--directory"},
            description = "where to write the generated files (current dir by default)")
    private String output = "";

    @Parameter(names = {"-p", "--package"}, description = CodegenConstants.API_PACKAGE_DESC)
    private String apiPackage;

    @Parameter(names = { "-h", "--help" }, hidden = true)
    private boolean helpFlag;

    @Parameter(names = "--debug", hidden = true)
    private String debugPort;

    @Override
    public void execute() {
        if (helpFlag) {
            String commandUsageInfo = BLauncherCmd.getCommandUsageInfo(parentCmdParser, "build");
            outStream.println(commandUsageInfo);
            return;
        }

        if (argList == null || argList.size() < 2) {
            throw LauncherUtils.createUsageException("Swagger action and a swagger file should be provided. " +
                    "Ex: ballerina swagger connector swagger_file");
        }
        String action = argList.get(0);
        switch (action) {
            case CONNECTOR:
                generateFromSwagger("ballerina-connector");
                break;
            case SKELETON:
                generateFromSwagger("ballerina-skeleton");
                break;
            case MOCK:
                generateFromSwagger("ballerina-mock-service");
                break;
            default:
                throw LauncherUtils.createUsageException("Only following actions(connector, skeleton, mock) are " +
                        "supported in swagger command");
        }
    }

    @Override
    public String getName() {
        return "swagger";
    }

    @Override
    public void printLongDesc(StringBuilder out) {
        out.append("Generates ballerina connector, service skeleton and mock service"+ System.lineSeparator());
        out.append("for a given swagger definition"+ System.lineSeparator());
        out.append(System.lineSeparator());
    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {
        stringBuilder.append("  ballerina swagger <connector | skeleton | mock> <swaggerFile> -p<package name> " +
                "-d<output directory name>\n");
        stringBuilder.append("\tconnector : generates a ballerina connector\n");
        stringBuilder.append("\tskeleton  : generates a ballerina service skeleton\n");
        stringBuilder.append("\tmock      : generates a ballerina mock service with sample responses\n");
    }

    private void generateFromSwagger(String targetLanguage){
        Generate generate = new Generate();
        generate.setSpec(argList.get(1));   //set swagger specification
        generate.setLang(targetLanguage);
        generate.setOutput(output);
        generate.setApiPackage(apiPackage);
        try {
            generate.run();
        } catch (Exception e) {
            String causeMessage = "";
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if(rootCause != null){
                causeMessage = rootCause.getMessage();
            }
            throw LauncherUtils.createUsageException("Error occurred when generating " + targetLanguage + " for " +
                    "swagger file at " + argList.get(1) + ". " + e.getMessage() + ". " + causeMessage);
        }
    }

    @Override
    public void setParentCmdParser(JCommander parentCmdParser) {
        this.parentCmdParser = parentCmdParser;
    }

    @Override
    public void setSelfCmdParser(JCommander selfCmdParser) {

    }
}
