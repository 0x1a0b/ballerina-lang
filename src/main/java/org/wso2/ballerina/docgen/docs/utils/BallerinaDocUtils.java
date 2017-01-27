/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.ballerina.docgen.docs.utils;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.wso2.ballerina.core.exception.LinkerException;
import org.wso2.ballerina.core.exception.SemanticException;
import org.wso2.ballerina.core.interpreter.SymScope;
import org.wso2.ballerina.core.model.BallerinaFile;
import org.wso2.ballerina.core.model.builder.BLangModelBuilder;
import org.wso2.ballerina.core.model.types.BArrayType;
import org.wso2.ballerina.core.model.types.BType;
import org.wso2.ballerina.core.model.types.TypeEnum;
import org.wso2.ballerina.core.parser.BallerinaLexer;
import org.wso2.ballerina.core.parser.BallerinaParser;
import org.wso2.ballerina.core.parser.BallerinaParserErrorStrategy;
import org.wso2.ballerina.core.parser.antlr4.BLangAntlr4Listener;
import org.wso2.ballerina.core.runtime.internal.BuiltInNativeConstructLoader;
import org.wso2.ballerina.core.runtime.internal.GlobalScopeHolder;
import org.wso2.ballerina.core.semantics.SemanticAnalyzer;

import java.io.FileInputStream;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Util methods used for doc generation.
 */
public class BallerinaDocUtils {

    private static final PrintStream out = System.out;

    /**
     * @return the string representation of a type
     */
    public static String getType(TypeEnum argType, TypeEnum argEltType) {
        if (TypeEnum.ARRAY.equals(argType)) {
            return argEltType.getName() + "[]";
        }
        return argType.getName();
    }

    /**
     * @return the string representation of a {@link BType}
     */
    public static String getType(BType type) {
        if (type instanceof BArrayType) {
            BArrayType t = (BArrayType) type;
            return t.getElementType().toString() + "[]";
        }
        return type.toString();
    }

    /**
     * TODO this method will be replaced once SameeraJ added the code to Core.
     */
    public static BallerinaFile buildLangModel(Path sourceFilePath) {
        try {
            if (sourceFilePath == null) {
                return null;
            }
            // Setting the name of the source file being parsed, to the ANTLR input stream.
            // This is required by the parser-error strategy.
            ANTLRInputStream antlrInputStream = new ANTLRInputStream(new FileInputStream(sourceFilePath.toFile()));

            //            if (antlrInputStream != null) {
            //                antlrInputStream.name = sourceFilePath.getFileName() == null ? "dummy" :
            // sourceFilePath.getFileName()
            //                        .toString();
            //            }

            BallerinaLexer ballerinaLexer = new BallerinaLexer(antlrInputStream);
            CommonTokenStream ballerinaToken = new CommonTokenStream(ballerinaLexer);

            BallerinaParser ballerinaParser = new BallerinaParser(ballerinaToken);
            ballerinaParser.setErrorHandler(new BallerinaParserErrorStrategy());

            BLangModelBuilder bLangModelBuilder = new BLangModelBuilder();
            BLangAntlr4Listener ballerinaBaseListener = new BLangAntlr4Listener(bLangModelBuilder);
            ballerinaParser.addParseListener(ballerinaBaseListener);
            ballerinaParser.compilationUnit();
            BallerinaFile balFile = bLangModelBuilder.build();

            BuiltInNativeConstructLoader.loadConstructs();
            SymScope globalScope = GlobalScopeHolder.getInstance().getScope();

            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(balFile, globalScope);
            balFile.accept(semanticAnalyzer);

            return balFile;
        } catch (ParseCancellationException | SemanticException | LinkerException e) {
            out.println(e.getMessage());

        } catch (Throwable e) {
            // TODO Fix this error message
            out.println(e.getMessage());
        }

        return null;
    }

}
