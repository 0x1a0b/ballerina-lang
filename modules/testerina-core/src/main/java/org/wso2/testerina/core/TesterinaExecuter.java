/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.testerina.core;

//import org.wso2.ballerina.core.exception.LinkerException;
import org.wso2.ballerina.core.exception.AssertionException;
import org.wso2.ballerina.core.interpreter.SymScope;
import org.wso2.ballerina.core.model.BallerinaFile;
//import org.wso2.ballerina.core.model.Function;
import org.wso2.ballerina.core.nativeimpl.lang.assertion.AssertFalse;
import org.wso2.ballerina.core.nativeimpl.lang.assertion.AssertTrue;
import org.wso2.ballerina.core.nativeimpl.lang.system.PrintlnInt;
import org.wso2.ballerina.core.nativeimpl.lang.system.PrintlnString;
//import org.wso2.ballerina.core.runtime.internal.BuiltInNativeConstructLoader;
//import org.wso2.ballerina.core.runtime.internal.GlobalScopeHolder;
import org.wso2.ballerina.core.utils.FunctionUtils;
import org.wso2.ballerina.core.utils.ParserUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

//import java.io.File;

public class TesterinaExecuter {

    private BallerinaFile bFile;

    public void executeTestFile(String resourcePath){
        SymScope symScope = new SymScope(null);
        FunctionUtils.addNativeFunction(symScope, new PrintlnInt());
        FunctionUtils.addNativeFunction(symScope, new PrintlnString());
        FunctionUtils.addNativeFunction(symScope, new AssertTrue());
        FunctionUtils.addNativeFunction(symScope,  new AssertFalse());


        //BuiltInNativeConstructLoader.loadConstructs();
        // SymScope globalSymScope = GlobalScopeHolder.getInstance().getScope();
        //bFile = ParserUtils.parseBalFile(File.separator+"home"+File.separator+"nirodha"+File.separator+"wso2"+File.separator+"bal"+File.separator+"BallerinaEcho"+File.separator+"Echo.bal", globalSymScope);
        bFile = ParserUtils.parseBalFile(resourcePath, symScope, true);
        TesterinaFile tFile = new TesterinaFile(getFileNameFromResourcePath(resourcePath), resourcePath, bFile);
        ArrayList<TesterinaFunction> testFunctions = tFile.getTestFunctions();
        Iterator<TesterinaFunction> testFuncIter = testFunctions.iterator();
        while(testFuncIter.hasNext()){
            TesterinaFunction tFunc = testFuncIter.next();
            System.out.println("Running '" + tFunc.getName() + "'...");
            try {
                tFunc.invoke();
            }catch (AssertionException e){
                System.out.println(e.getMessage());
            }
        }

        /*
        Function[] functions = bFile.getFunctions();
        for(int i=0; i < functions.length; i++){
            String name = functions[i].getFunctionName();
            if(name.startsWith("test")){
                System.out.println(name);
            }

        }
        //bFile = ParserUtils.parseBalFile("/home/nirodha/wso2/bal/BallerinaEcho/Echo.bal", symScope, true);

        BValue[] args = {};
        BValue[] returns = {};
        try {
            returns = Functions.invoke(bFile, funcToTest, args);
        } catch (AssertionException e){
            System.out.println(e.getMessage());
        }

        //Functions.getFunction()
        //String response = returns[0].stringValue();
        //System.out.println(response);
        */
    }

    private String getFileNameFromResourcePath(String path){
        int indexOfLastSeparator = path.lastIndexOf(File.separator);
        String fileName = path.substring(indexOfLastSeparator+1);
        return fileName;
    }

    /*
    public void executeTest(){
        SymScope symScope = new SymScope(null);
        FunctionUtils.addNativeFunction(symScope, new PrintlnInt());
        FunctionUtils.addNativeFunction(symScope, new PrintlnString());
        FunctionUtils.addNativeFunction(symScope, new AssertTrue());
        FunctionUtils.addNativeFunction(symScope,  new AssertFalse());
        //BuiltInNativeConstructLoader.loadConstructs();
       // SymScope globalSymScope = GlobalScopeHolder.getInstance().getScope();
        //bFile = ParserUtils.parseBalFile(File.separator+"home"+File.separator+"nirodha"+File.separator+"wso2"+File.separator+"bal"+File.separator+"BallerinaEcho"+File.separator+"Echo.bal", globalSymScope);
        bFile = ParserUtils.parseBalFile("testerina.resources/Echo.bal", symScope);
        Function[] functions = bFile.getFunctions();
        for(int i=0; i < functions.length; i++){
            String name = functions[i].getFunctionName();
            if(name.startsWith("test")){
                System.out.println(name);
            }

        }
        //bFile = ParserUtils.parseBalFile("/home/nirodha/wso2/bal/BallerinaEcho/Echo.bal", symScope, true);

        BValue[] args = {};
        BValue[] returns = {};
        try {
            returns = Functions.invoke(bFile, funcToTest, args);
        } catch (AssertionException e){
            System.out.println(e.getMessage());
        }

        //Functions.getFunction()
        //String response = returns[0].stringValue();
        //System.out.println(response);
    }
    */
}

