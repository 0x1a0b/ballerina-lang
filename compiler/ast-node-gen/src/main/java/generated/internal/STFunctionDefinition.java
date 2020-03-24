/*
*  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package generated.internal;
import generated.facade.*;

public  class STFunctionDefinition extends STNode{
public final STNode visibilityQual;
public final STToken functionKeyword;
public final STNode functionName;
public final STToken openParenToken;
public final STNode parameters;
public final STToken closeParenToken;
public final STNode returnTypeDescriptor;
public final STNode functionBody;

public STFunctionDefinition(SyntaxKind kind , STNode visibilityQual, STToken functionKeyword, STNode functionName, STToken openParenToken, STNode parameters, STToken closeParenToken, STNode returnTypeDescriptor, STNode functionBody){
super(kind );
this.visibilityQual = visibilityQual;
this.functionKeyword = functionKeyword;
this.functionName = functionName;
this.openParenToken = openParenToken;
this.parameters = parameters;
this.closeParenToken = closeParenToken;
this.returnTypeDescriptor = returnTypeDescriptor;
this.functionBody = functionBody;
this.bucketCount = 8;
this.childBuckets = new STNode[8];
this.addChildNode(visibilityQual, 0);
this.addChildNode(functionKeyword, 1);
this.addChildNode(functionName, 2);
this.addChildNode(openParenToken, 3);
this.addChildNode(parameters, 4);
this.addChildNode(closeParenToken, 5);
this.addChildNode(returnTypeDescriptor, 6);
this.addChildNode(functionBody, 7);
}

public STFunctionDefinition(SyntaxKind kind, int width , STNode visibilityQual, STToken functionKeyword, STNode functionName, STToken openParenToken, STNode parameters, STToken closeParenToken, STNode returnTypeDescriptor, STNode functionBody) {
super(kind, width );
this.visibilityQual = visibilityQual;
this.functionKeyword = functionKeyword;
this.functionName = functionName;
this.openParenToken = openParenToken;
this.parameters = parameters;
this.closeParenToken = closeParenToken;
this.returnTypeDescriptor = returnTypeDescriptor;
this.functionBody = functionBody;
this.bucketCount = 8;
this.childBuckets = new STNode[8];
this.addChildNode(visibilityQual, 0);
this.addChildNode(functionKeyword, 1);
this.addChildNode(functionName, 2);
this.addChildNode(openParenToken, 3);
this.addChildNode(parameters, 4);
this.addChildNode(closeParenToken, 5);
this.addChildNode(returnTypeDescriptor, 6);
this.addChildNode(functionBody, 7);
}


public Node createFacade(int position, NonTerminalNode parent) {
return new FunctionDefinition(this, position, parent);
}
}
