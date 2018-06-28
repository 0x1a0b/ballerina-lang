/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.ballerinalang.compiler.desugar;

import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.tree.clauses.SelectExpressionNode;
import org.ballerinalang.model.tree.clauses.WhereNode;
import org.ballerinalang.model.tree.statements.StatementNode;
import org.ballerinalang.model.types.TypeKind;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolEnter;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolResolver;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BConversionOperatorSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BRecordTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStreamType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangSelectClause;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangSelectExpression;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangStreamAction;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangStreamingInput;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangWhere;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBinaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeConversionExpr;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangExpressionStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForever;
import org.wso2.ballerinalang.compiler.tree.statements.BLangReturn;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStreamingQueryStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangVariableDef;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;
import org.wso2.ballerinalang.compiler.tree.types.BLangValueType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.FieldKind;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Class responsible for desugar an iterable chain into actual Ballerina code.
 *
 * @since 0.961.0
 */
public class StreamingCodeDesugar extends BLangNodeVisitor {

    private static final String FUNC_CALLER = "$lambda$streaming";
    private static final String OUTPUT_FUNC_REFERENCE = "$lambda$streaming$output$function";
    private static final String OUTPUT_PROCESS_FUNC_REFERENCE = "$lambda$streaming$output$process";
    private static final String FILTER_FUNC_REFERENCE = "$lambda$streaming$filter";
    private static final String SIMPLE_SELECT_FUNC_REFERENCE = "$lambda$streaming$simple$select";
    private static final String INPUT_STREAM_PARAM_REFERENCE = "$lambda$streaming$input$variable";
    private static final String FILTER_LAMBDA_PARAM_REFERENCE = "$lambda$streaming$filter$input$variable";
    private static final String SIMPLE_SELECT_LAMBDA_PARAM_REFERENCE = "$lambda$streaming$simple$select$input$variable";
    private static final String STREAM_EVENT_ARRAY_PARAM_REFERENCE = "$lambda$streaming$stream$event$variable";
    private static final String OUTPUT_EVENT_SELECTOR_PARAM_REFERENCE =
            "$lambda$streaming$output$event$selector$variable";
    private static final String STREAMS_STDLIB_PACKAGE_NAME = "streams";
    private static final String NEXT_PROCESS_METHOD_NAME = "process";
    private static final String STREAM_EVENT_OBJECT_NAME = "StreamEvent";

    private static final CompilerContext.Key<StreamingCodeDesugar> STREAMING_DESUGAR_KEY =
            new CompilerContext.Key<>();

    private Desugar parentDesugar;
    private final SymbolTable symTable;
    private final SymbolResolver symResolver;
    private final SymbolEnter symbolEnter;
    private final Names names;
    private int lambdaFunctionCount = 0;
    private SymbolEnv env;
    private List<BLangStatement> stmts;
    private Set<BVarSymbol> closureVarSymbols = new LinkedHashSet<>();
    private BType inputStreamEventType;
    private BLangExpression filterConditionalExpression;
    private BLangVariable filterTypeCastedVariable;
    private BLangVariable outputEventTypeVariable;
    private Stack<BVarSymbol> nextProcessVarSymbolStack = new Stack<>();


    private StreamingCodeDesugar(CompilerContext context) {
        context.put(STREAMING_DESUGAR_KEY, this);
        this.symTable = SymbolTable.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.symbolEnter = SymbolEnter.getInstance(context);
        this.names = Names.getInstance(context);
    }

    public static StreamingCodeDesugar getInstance(CompilerContext context) {
        StreamingCodeDesugar desugar = context.get(STREAMING_DESUGAR_KEY);
        if (desugar == null) {
            desugar = new StreamingCodeDesugar(context);
        }

        return desugar;
    }

    public BLangBlockStmt desugar(BLangForever foreverStatement, Desugar desugar) {

        this.parentDesugar = desugar;
        this.env = foreverStatement.getEnv();
        stmts = new ArrayList<>();
        List<? extends StatementNode> statementNodes = foreverStatement.getStreamingQueryStatements();

        // Generate Streaming Consumer Function
        statementNodes.forEach(statementNode -> ((BLangStatement) statementNode).accept(this));
        return ASTBuilderUtil.createBlockStmt(foreverStatement.pos, stmts);
    }

    @Override
    public void visit(BLangStreamingQueryStatement streamingQueryStatement) {

        closureVarSymbols = new LinkedHashSet<>();
        inputStreamEventType = null;
        filterConditionalExpression = null;
        filterTypeCastedVariable = null;
        outputEventTypeVariable = null;

        inputStreamEventType = ((BStreamType) ((BLangSimpleVarRef) streamingQueryStatement.getStreamingInput().
                getStreamReference()).type).constraint;

        //Construct the elements to publish events to output stream
        BLangStreamAction streamAction = (BLangStreamAction) streamingQueryStatement.getStreamingAction();
        streamAction.accept(this);

        BLangSelectClause selectClause = (BLangSelectClause) streamingQueryStatement.getSelectClause();
        selectClause.accept(this);

        //Build elements to consume events from input stream
        BLangStreamingInput streamingInput = (BLangStreamingInput) streamingQueryStatement.getStreamingInput();
        streamingInput.accept(this);
    }

    @Override
    public void visit(BLangStreamAction streamAction) {
        BLangLambdaFunction lambdaFunction = (BLangLambdaFunction) streamAction.getInvokableBody();
        lambdaFunction.accept(this);
    }

    @Override
    public void visit(BLangLambdaFunction lambdaFunction) {

        //Create lambda function Variable
        BLangVariable oldFunctionVariable = (BLangVariable) lambdaFunction.getFunctionNode().getParameters().get(0);
        BLangVariable lambdaFunctionVariable = this.createAnyTypeVariable(oldFunctionVariable.symbol.name.getValue(),
                lambdaFunction.pos, env);

        //Create new lambda function to process the output events
        BLangLambdaFunction outputLambdaFunction = (BLangLambdaFunction) TreeBuilder.createLambdaFunctionNode();
        BLangFunction outputLambdaFunctionNode = ASTBuilderUtil.createFunction(lambdaFunction.pos,
                getFunctionName(FUNC_CALLER));
        outputLambdaFunction.function = outputLambdaFunctionNode;
        BLangBlockStmt lambdaBody = ASTBuilderUtil.createBlockStmt(lambdaFunction.pos);

        //New Lambda Function
        outputLambdaFunctionNode.requiredParams.add(lambdaFunctionVariable);
        outputLambdaFunctionNode.returnTypeNode = ASTBuilderUtil.createTypeNode(symTable.nilType);
        BLangValueType returnType = new BLangValueType();
        returnType.setTypeKind(TypeKind.NIL);
        outputLambdaFunctionNode.setReturnTypeNode(returnType);
        defineFunction(outputLambdaFunctionNode, env.enclPkg);

        outputLambdaFunctionNode.body = lambdaBody;
        outputLambdaFunctionNode.closureVarSymbols = closureVarSymbols;
        outputLambdaFunctionNode.desugared = false;
        outputLambdaFunction.pos = lambdaFunction.pos;
        outputLambdaFunction.type = symTable.anyType;


        //Create type casting for the output variable
        BVarSymbol typeCastingVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(oldFunctionVariable.getName().getValue())),
                lambdaFunctionVariable.symbol.pkgID, ((BArrayType) oldFunctionVariable.type).eType, env.scope.owner);
        BLangSimpleVarRef typeCastingSimpleVarRef = ASTBuilderUtil.createVariableRef(lambdaFunction.pos,
                lambdaFunctionVariable.symbol);
        BLangExpression typeCastingExpression = generateConversionExpr(typeCastingSimpleVarRef,
                ((BArrayType) oldFunctionVariable.type).eType, symResolver);
        BLangVariable typeCastingVariable = ASTBuilderUtil.
                createVariable(lambdaFunction.pos, getVariableName(oldFunctionVariable.getName().getValue()),
                        ((BArrayType) oldFunctionVariable.type).eType, typeCastingExpression, typeCastingVarSymbol);
        typeCastingVariable.typeNode = ASTBuilderUtil.createTypeNode(((BArrayType) oldFunctionVariable.type).eType);
        BLangVariableDef typeCastingVariableDef = ASTBuilderUtil.createVariableDef(lambdaFunction.pos,
                typeCastingVariable);
        outputEventTypeVariable = typeCastingVariable;

        lambdaBody.stmts.add(typeCastingVariableDef);

        //Publish events to stream
        BLangExpressionStmt eventPublisherStatement = (BLangExpressionStmt) TreeBuilder.
                createExpressionStatementNode();
        eventPublisherStatement.pos = lambdaFunction.pos;
        BInvokableSymbol publisherMethodSymbol = (BInvokableSymbol) symTable.rootScope.lookup(names.
                fromString("stream.publish")).symbol;

        List<BLangVariable> variables = new ArrayList<>(1);
        variables.add(typeCastingVariable);
        BLangInvocation invocationExpr = ASTBuilderUtil.
                createInvocationExpr(lambdaFunction.pos, publisherMethodSymbol, variables, symResolver);
        BVarSymbol varSymbol = ((BLangSimpleVarRef.BLangPackageVarRef)
                ((BLangInvocation.BLangAttachedFunctionInvocation)
                        ((BLangExpressionStmt) lambdaFunction.getFunctionNode().getBody().getStatements().get(0)).
                                expr).expr).varSymbol;
        invocationExpr.expr = ASTBuilderUtil.createVariableRef(lambdaFunction.pos, varSymbol);
        eventPublisherStatement.expr = invocationExpr;

        lambdaBody.stmts.add(eventPublisherStatement);

        //Create Function definition statement
        BLangVariable outputStreamFunctionVariable = ASTBuilderUtil.
                createVariable(lambdaFunction.pos, getVariableName(OUTPUT_FUNC_REFERENCE),
                        outputLambdaFunction.type, outputLambdaFunction,
                        outputLambdaFunction.function.symbol);
        outputStreamFunctionVariable.typeNode = ASTBuilderUtil.createTypeNode(outputLambdaFunctionNode.type);
        BLangVariableDef outputStreamFunctionVarDef = ASTBuilderUtil.createVariableDef(lambdaFunction.pos,
                outputStreamFunctionVariable);

        stmts.add(outputStreamFunctionVarDef);

        //Create output event process definition
        BLangSimpleVarRef outputStreamFunctionSimpleVarRef = ASTBuilderUtil.createVariableRef(lambdaFunction.pos,
                outputStreamFunctionVariable.symbol);
        BInvokableSymbol outputProcessInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(lambdaFunction.pos, env, names.fromString(STREAMS_STDLIB_PACKAGE_NAME)).
                scope.lookup(new Name("createOutputProcess")).symbol;

        BType outputProcessInvokableType = outputProcessInvokableSymbol.type.getReturnType();
        BVarSymbol outputProcessInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(OUTPUT_PROCESS_FUNC_REFERENCE)), outputProcessInvokableSymbol.pkgID,
                outputProcessInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(outputProcessInvokableTypeVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(outputStreamFunctionSimpleVarRef);
        BLangInvocation outputProcessMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(lambdaFunction.pos, outputProcessInvokableSymbol, args,
                        symResolver);
        outputProcessMethodInvocation.argExprs = args;
        BLangVariable outputProcessInvokableTypeVariable = ASTBuilderUtil.
                createVariable(lambdaFunction.pos, getVariableName(OUTPUT_PROCESS_FUNC_REFERENCE),
                        outputProcessInvokableType, outputProcessMethodInvocation, outputProcessInvokableTypeVarSymbol);


        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(lambdaFunction.pos, "OutputProcess");
        userDefinedType.type = outputProcessInvokableType;
        outputProcessInvokableTypeVariable.setTypeNode(userDefinedType);
        BLangVariableDef outputProcessInvokableTypeVariableDef = ASTBuilderUtil.createVariableDef(lambdaFunction.pos,
                outputProcessInvokableTypeVariable);

        stmts.add(outputProcessInvokableTypeVariableDef);
    }

    @Override
    public void visit(BLangSelectClause selectClause) {

        //Create lambda function Variable
        BLangVariable lambdaFunctionVariable =
                this.createStreamEventTypeVariable(getVariableName(SIMPLE_SELECT_LAMBDA_PARAM_REFERENCE),
                        selectClause.pos, env);

        //Create new lambda function to process the output events
        BLangLambdaFunction simpleSelectLambdaFunction = (BLangLambdaFunction) TreeBuilder.createLambdaFunctionNode();
        BLangFunction simpleSelectLambdaFunctionNode = ASTBuilderUtil.createFunction(selectClause.pos,
                getFunctionName(FUNC_CALLER));
        simpleSelectLambdaFunction.function = simpleSelectLambdaFunctionNode;
        BLangBlockStmt lambdaBody = ASTBuilderUtil.createBlockStmt(selectClause.pos);
        simpleSelectLambdaFunctionNode.requiredParams.add(lambdaFunctionVariable);
        simpleSelectLambdaFunctionNode.returnTypeNode = ASTBuilderUtil.createTypeNode(symTable.anyType);
        BLangValueType returnType = new BLangValueType();
        returnType.setTypeKind(TypeKind.ANY);
        simpleSelectLambdaFunctionNode.setReturnTypeNode(returnType);
        defineFunction(simpleSelectLambdaFunctionNode, env.enclPkg);

        simpleSelectLambdaFunctionNode.body = lambdaBody;
        simpleSelectLambdaFunctionNode.closureVarSymbols.add(lambdaFunctionVariable.symbol);
        simpleSelectLambdaFunctionNode.desugared = false;
        simpleSelectLambdaFunction.pos = selectClause.pos;
        simpleSelectLambdaFunction.type = simpleSelectLambdaFunctionNode.symbol.type;

        //Create type casting for the filter variable
        BVarSymbol typeCastingVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(lambdaFunctionVariable.getName().getValue())),
                lambdaFunctionVariable.symbol.pkgID, inputStreamEventType, env.scope.owner);

        BLangFieldBasedAccess eventObjectField = (BLangFieldBasedAccess) TreeBuilder.createFieldBasedAccessNode();
        eventObjectField.expr = ASTBuilderUtil.createVariableRef(selectClause.pos, lambdaFunctionVariable.symbol);
        eventObjectField.type = symTable.anyType;
        eventObjectField.symbol = ((BRecordType) (lambdaFunctionVariable).type).fields.get(1).symbol;
        eventObjectField.fieldKind = FieldKind.SINGLE;
        eventObjectField.pos = selectClause.pos;
        eventObjectField.field = ASTBuilderUtil.createIdentifier(selectClause.pos, "eventObject");

        BLangExpression typeCastingExpression = generateConversionExpr(eventObjectField, inputStreamEventType,
                symResolver);

        BLangVariable typeCastingVariable = ASTBuilderUtil.
                createVariable(selectClause.pos, getVariableName(lambdaFunctionVariable.getName().getValue()),
                        inputStreamEventType, typeCastingExpression, typeCastingVarSymbol);

        typeCastingVariable.typeNode = ASTBuilderUtil.createTypeNode(inputStreamEventType);
        BLangVariableDef typeCastingVariableDef = ASTBuilderUtil.createVariableDef(selectClause.pos,
                typeCastingVariable);
        lambdaBody.stmts.add(typeCastingVariableDef);

        //Output object creation
        BLangRecordLiteral outputEventRecordLiteral = ASTBuilderUtil.createEmptyRecordLiteral(selectClause.pos,
                outputEventTypeVariable.type);
        List<BLangRecordLiteral.BLangRecordKeyValue> recordKeyValueList = new ArrayList<>();

        List<? extends SelectExpressionNode> selectExprList = selectClause.getSelectExpressions();
        for (SelectExpressionNode expressionNode : selectExprList) {
            BLangSelectExpression selectExpression = (BLangSelectExpression) expressionNode;
            BLangRecordLiteral.BLangRecordKeyValue recordKeyValue = (BLangRecordLiteral.BLangRecordKeyValue)
                    TreeBuilder.createRecordKeyValue();

            if (selectExpression.getIdentifier() != null) {
                BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
                varRef.variableName = ASTBuilderUtil.createIdentifier(selectClause.pos,
                        selectExpression.getIdentifier());
                recordKeyValue.key = new BLangRecordLiteral.BLangRecordKey(varRef);
                recordKeyValue.key.fieldSymbol = getOutputEventFieldSymbol(outputEventTypeVariable,
                        selectExpression.getIdentifier());
            } else {
                BLangSimpleVarRef varRef = (BLangSimpleVarRef) TreeBuilder.createSimpleVariableReferenceNode();
                varRef.variableName = ((BLangFieldBasedAccess) selectExpression.getExpression()).field;
                recordKeyValue.key = new BLangRecordLiteral.BLangRecordKey(varRef);
                recordKeyValue.key.fieldSymbol = getOutputEventFieldSymbol(outputEventTypeVariable,
                        ((BLangFieldBasedAccess) selectExpression.getExpression()).field.value);
            }

            if (selectExpression.getExpression() instanceof BLangFieldBasedAccess) {
                BLangFieldBasedAccess fieldAccessExpr = ((BLangFieldBasedAccess) selectExpression.getExpression());
                BLangSimpleVarRef varRef = ASTBuilderUtil.createVariableRef(selectExpression.pos,
                        typeCastingVariable.symbol);
                BLangExpression selectFieldExpression = ASTBuilderUtil.createFieldAccessExpr(varRef,
                        fieldAccessExpr.field);
                ((BLangFieldBasedAccess) selectFieldExpression).symbol = fieldAccessExpr.symbol;
                (selectFieldExpression).type = fieldAccessExpr.type;
                recordKeyValue.valueExpr = selectFieldExpression;
            } else {
                recordKeyValue.valueExpr = (BLangLiteral) selectExpression.getExpression();
            }

            recordKeyValueList.add(recordKeyValue);
        }

        BVarSymbol outputEventVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(OUTPUT_EVENT_SELECTOR_PARAM_REFERENCE)),
                lambdaFunctionVariable.symbol.pkgID, outputEventTypeVariable.type, env.scope.owner);
        outputEventRecordLiteral.keyValuePairs = recordKeyValueList;
        BLangVariable outputEventObjectVariable = ASTBuilderUtil.
                createVariable(selectClause.pos, getVariableName(OUTPUT_EVENT_SELECTOR_PARAM_REFERENCE),
                        outputEventTypeVariable.type, outputEventRecordLiteral, outputEventVarSymbol);

        outputEventObjectVariable.typeNode = ASTBuilderUtil.createTypeNode(outputEventTypeVariable.type);
        BLangVariableDef outputEventObjectVariableDef = ASTBuilderUtil.createVariableDef(selectClause.pos,
                outputEventObjectVariable);
        lambdaBody.stmts.add(outputEventObjectVariableDef);

        // Return statement with newly created output event
        BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
        returnStmt.pos = selectClause.pos;
        returnStmt.expr = ASTBuilderUtil.createVariableRef(selectClause.pos,
                outputEventObjectVariable.symbol);
        lambdaBody.stmts.add(returnStmt);


        //Create event simple selector definition
        BVarSymbol nextProcessInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
        List<BAttachedFunction> attachedFunctionsList = ((BObjectTypeSymbol)
                (nextProcessInvokableTypeVarSymbol).type.tsymbol).attachedFuncs;
        BInvokableSymbol nextProcessInvokableSymbol = null;
        for (BAttachedFunction attachedFunction : attachedFunctionsList) {
            if (attachedFunction.funcName.toString().equals(NEXT_PROCESS_METHOD_NAME)) {
                nextProcessInvokableSymbol = attachedFunction.symbol;
            }
        }

        BLangSimpleVarRef nextProcessSimpleVarRef = ASTBuilderUtil.createVariableRef(selectClause.pos,
                nextProcessInvokableTypeVarSymbol);
        BLangFieldBasedAccess nextProcessMethodAccess = (BLangFieldBasedAccess)
                TreeBuilder.createFieldBasedAccessNode();
        nextProcessMethodAccess.expr = nextProcessSimpleVarRef;
        nextProcessMethodAccess.symbol = nextProcessInvokableSymbol;
        nextProcessMethodAccess.type = nextProcessInvokableSymbol.type;
        nextProcessMethodAccess.pos = selectClause.pos;
        nextProcessMethodAccess.field = ASTBuilderUtil.createIdentifier(selectClause.pos, NEXT_PROCESS_METHOD_NAME);

        BInvokableSymbol simpleSelectInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(selectClause.pos, env, names.fromString(STREAMS_STDLIB_PACKAGE_NAME)).
                scope.lookup(new Name("createSimpleSelect")).symbol;

        BType simpleSelectInvokableType = simpleSelectInvokableSymbol.type.getReturnType();
        BVarSymbol simpleSelectInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(SIMPLE_SELECT_FUNC_REFERENCE)), simpleSelectInvokableSymbol.pkgID,
                simpleSelectInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(simpleSelectInvokableTypeVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(nextProcessMethodAccess);
        args.add(simpleSelectLambdaFunction);

        BLangInvocation simpleSelectMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(selectClause.pos, simpleSelectInvokableSymbol, args,
                        symResolver);
        simpleSelectMethodInvocation.argExprs = args;
        BLangVariable simpleSelectInvokableTypeVariable = ASTBuilderUtil.
                createVariable(selectClause.pos, getVariableName(SIMPLE_SELECT_FUNC_REFERENCE),
                        simpleSelectInvokableType, simpleSelectMethodInvocation, simpleSelectInvokableTypeVarSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(selectClause.pos, "SimpleSelect");
        userDefinedType.type = simpleSelectInvokableType;
        simpleSelectInvokableTypeVariable.setTypeNode(userDefinedType);
        BLangVariableDef simpleSelectInvokableTypeVariableDef = ASTBuilderUtil.createVariableDef(selectClause.pos,
                simpleSelectInvokableTypeVariable);
        stmts.add(simpleSelectInvokableTypeVariableDef);
    }


    @Override
    public void visit(BLangStreamingInput streamingInput) {

        //Lambda function parameter
        BType lambdaParameterType = inputStreamEventType;

        WhereNode beforeWhereNode = streamingInput.getAfterStreamingCondition();
        if (beforeWhereNode != null) {
            ((BLangWhere) beforeWhereNode).accept(this);
        }

        //Construct lambda function which consumes events
        BLangLambdaFunction streamSubscriberLambdaFunction = (BLangLambdaFunction) TreeBuilder.
                createLambdaFunctionNode();
        BLangFunction streamSubscriberLambdaFunctionNode = ASTBuilderUtil.createFunction(streamingInput.pos,
                getFunctionName(FUNC_CALLER));
        streamSubscriberLambdaFunction.function = streamSubscriberLambdaFunctionNode;
        BLangBlockStmt lambdaBody = ASTBuilderUtil.createBlockStmt(streamingInput.pos);


        BVarSymbol lambdaParameterVarSymbol = new BVarSymbol(0, new Name(getVariableName(INPUT_STREAM_PARAM_REFERENCE)),
                lambdaParameterType.tsymbol.pkgID, lambdaParameterType, env.scope.owner);

        BLangVariable inputStreamLambdaFunctionVariable = ASTBuilderUtil.createVariable(streamingInput.pos,
                getVariableName(INPUT_STREAM_PARAM_REFERENCE), lambdaParameterType, null, lambdaParameterVarSymbol);
        inputStreamLambdaFunctionVariable.typeNode = ASTBuilderUtil.createTypeNode(lambdaParameterType);


        //New Lambda Function
        streamSubscriberLambdaFunctionNode.requiredParams.add(inputStreamLambdaFunctionVariable);
        streamSubscriberLambdaFunctionNode.returnTypeNode = ASTBuilderUtil.createTypeNode(symTable.nilType);
        BLangValueType returnType = new BLangValueType();
        returnType.setTypeKind(TypeKind.NIL);
        streamSubscriberLambdaFunction.type = streamSubscriberLambdaFunctionNode.type;
        streamSubscriberLambdaFunctionNode.setReturnTypeNode(returnType);
        defineFunction(streamSubscriberLambdaFunctionNode, env.enclPkg);

        //Implement the lambdaBody

        //Event conversion to StreamEvent
        BLangSimpleVarRef lambdaParameterSimpleVarRef = ASTBuilderUtil.createVariableRef(streamingInput.pos,
                inputStreamLambdaFunctionVariable.symbol);
        BInvokableSymbol streamEventBuilderInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(streamingInput.pos, env, names.fromString(STREAMS_STDLIB_PACKAGE_NAME)).
                scope.lookup(new Name("buildStreamEvent")).symbol;

        BType streamEventArrayType = streamEventBuilderInvokableSymbol.type.getReturnType();
        BVarSymbol streamEventArrayTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(STREAM_EVENT_ARRAY_PARAM_REFERENCE)), streamEventBuilderInvokableSymbol.pkgID,
                streamEventArrayType, env.scope.owner);
        List<BLangExpression> args = new ArrayList<>();
        args.add(lambdaParameterSimpleVarRef);


        BLangInvocation streamEventBuilderMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(streamingInput.pos, streamEventBuilderInvokableSymbol, args,
                        symResolver);
        streamEventBuilderMethodInvocation.argExprs = args;

        BLangVariable streamEventArrayTypeVariable = ASTBuilderUtil.
                createVariable(streamingInput.pos, getVariableName(STREAM_EVENT_ARRAY_PARAM_REFERENCE),
                        streamEventArrayType, streamEventBuilderMethodInvocation, streamEventArrayTypeVarSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(streamingInput.pos, STREAM_EVENT_OBJECT_NAME);
        userDefinedType.type = streamEventArrayType;
        streamEventArrayTypeVariable.setTypeNode(userDefinedType);
        BLangVariableDef streamEventArrayTypeVariableDef = ASTBuilderUtil.createVariableDef(streamingInput.pos,
                streamEventArrayTypeVariable);

        lambdaBody.stmts.add(streamEventArrayTypeVariableDef);

        //Function invocation to call output process
        BVarSymbol filterInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
        List<BAttachedFunction> attachedFunctionsList = ((BObjectTypeSymbol)
                (filterInvokableTypeVarSymbol).type.tsymbol).attachedFuncs;
        BInvokableSymbol nextProcessInvokableSymbol = null;
        for (BAttachedFunction attachedFunction : attachedFunctionsList) {
            if (attachedFunction.funcName.toString().equals(NEXT_PROCESS_METHOD_NAME)) {
                nextProcessInvokableSymbol = attachedFunction.symbol;
            }
        }

        BLangSimpleVarRef streamEventArrayRef = ASTBuilderUtil.createVariableRef(streamingInput.pos,
                streamEventArrayTypeVarSymbol);
        List<BLangExpression> nextProcessVariables = new ArrayList<>(1);
        nextProcessVariables.add(streamEventArrayRef);
        BLangInvocation nextProcessMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(streamingInput.pos, nextProcessInvokableSymbol, nextProcessVariables,
                        symResolver);
        nextProcessMethodInvocation.argExprs = nextProcessVariables;


        nextProcessMethodInvocation.expr = ASTBuilderUtil.createVariableRef(streamingInput.pos,
                filterInvokableTypeVarSymbol);
        BLangExpressionStmt nextProcessExpressionStmt = (BLangExpressionStmt) TreeBuilder.
                createExpressionStatementNode();
        nextProcessExpressionStmt.pos = streamingInput.pos;
        nextProcessExpressionStmt.expr = nextProcessMethodInvocation;
        lambdaBody.stmts.add(nextProcessExpressionStmt);

        //Set lambda body
        streamSubscriberLambdaFunctionNode.body = lambdaBody;
        streamSubscriberLambdaFunctionNode.closureVarSymbols.add(filterInvokableTypeVarSymbol);
        streamSubscriberLambdaFunctionNode.closureVarSymbols.add(inputStreamLambdaFunctionVariable.symbol);
        streamSubscriberLambdaFunctionNode.desugared = false;
        streamSubscriberLambdaFunction.pos = streamingInput.pos;
        streamSubscriberLambdaFunction.type = symTable.anyType;

        //Create function call - stream1.subscribe(lambda_function)
        BLangExpressionStmt inputStreamSubscribeStatement = (BLangExpressionStmt) TreeBuilder.
                createExpressionStatementNode();
        inputStreamSubscribeStatement.pos = streamingInput.pos;
        BInvokableSymbol subscribeMethodSymbol = (BInvokableSymbol) symTable.rootScope.
                lookup(names.fromString("stream.subscribe"))
                .symbol;
        List<BLangExpression> variables = new ArrayList<>(1);
        variables.add(streamSubscriberLambdaFunction);
        BLangInvocation invocationExpr = ASTBuilderUtil.
                createInvocationExprForMethod(streamingInput.pos, subscribeMethodSymbol, variables,
                        symResolver);

        invocationExpr.argExprs = variables;
        invocationExpr.expr = ASTBuilderUtil.createVariableRef(streamingInput.pos, (BVarSymbol)
                ((BLangSimpleVarRef) streamingInput.getStreamReference()).symbol);
        inputStreamSubscribeStatement.expr = invocationExpr;

        //Add stream subscriber function to stmts
        stmts.add(inputStreamSubscribeStatement);
    }

    @Override
    public void visit(BLangWhere where) {

        //Create lambda function Variable
        BLangVariable lambdaFunctionVariable =
                this.createAnyTypeVariable(getVariableName(FILTER_LAMBDA_PARAM_REFERENCE), where.pos, env);

        //Create new lambda function to process the output events
        BLangLambdaFunction filterLambdaFunction = (BLangLambdaFunction) TreeBuilder.createLambdaFunctionNode();
        BLangFunction filterLambdaFunctionNode = ASTBuilderUtil.createFunction(where.pos,
                getFunctionName(FUNC_CALLER));
        filterLambdaFunction.function = filterLambdaFunctionNode;
        BLangBlockStmt lambdaBody = ASTBuilderUtil.createBlockStmt(where.pos);
        filterLambdaFunctionNode.requiredParams.add(lambdaFunctionVariable);
        filterLambdaFunctionNode.returnTypeNode = ASTBuilderUtil.createTypeNode(symTable.booleanType);
        BLangValueType returnType = new BLangValueType();
        returnType.setTypeKind(TypeKind.BOOLEAN);
        filterLambdaFunctionNode.setReturnTypeNode(returnType);
        defineFunction(filterLambdaFunctionNode, env.enclPkg);

        filterLambdaFunctionNode.body = lambdaBody;
        filterLambdaFunctionNode.closureVarSymbols.add(lambdaFunctionVariable.symbol);
        filterLambdaFunctionNode.desugared = false;
        filterLambdaFunction.pos = where.pos;
        filterLambdaFunction.type = symTable.anyType;


        //Create type casting for the filter variable
        BVarSymbol typeCastingVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(lambdaFunctionVariable.getName().getValue())),
                lambdaFunctionVariable.symbol.pkgID, inputStreamEventType, env.scope.owner);

        BLangSimpleVarRef typeCastingSimpleVarRef = ASTBuilderUtil.createVariableRef(where.pos,
                lambdaFunctionVariable.symbol);

        BLangExpression typeCastingExpression = generateConversionExpr(typeCastingSimpleVarRef,
                inputStreamEventType, symResolver);

        BLangVariable typeCastingVariable = ASTBuilderUtil.
                createVariable(where.pos, getVariableName(lambdaFunctionVariable.getName().getValue()),
                        inputStreamEventType, typeCastingExpression, typeCastingVarSymbol);

        typeCastingVariable.typeNode = ASTBuilderUtil.createTypeNode(inputStreamEventType);
        BLangVariableDef typeCastingVariableDef = ASTBuilderUtil.createVariableDef(where.pos,
                typeCastingVariable);
        filterTypeCastedVariable = typeCastingVariable;
        lambdaBody.stmts.add(typeCastingVariableDef);


        // Return statement with filter condition
        BLangReturn returnStmt = (BLangReturn) TreeBuilder.createReturnNode();
        returnStmt.pos = where.pos;

        final BLangBinaryExpr binaryExpr = (BLangBinaryExpr) TreeBuilder.createBinaryExpressionNode();
        binaryExpr.pos = where.pos;
        binaryExpr.type = symTable.booleanType;
        binaryExpr.opKind = ((BLangBinaryExpr) where.getExpression()).getOperatorKind();
        ((BLangBinaryExpr) where.getExpression()).getLeftExpression().accept(this);
        binaryExpr.lhsExpr = filterConditionalExpression;
        ((BLangBinaryExpr) where.getExpression()).getRightExpression().accept(this);
        binaryExpr.rhsExpr = filterConditionalExpression;
        binaryExpr.opSymbol = ((BLangBinaryExpr) where.getExpression()).opSymbol;
        returnStmt.expr = binaryExpr;
        lambdaBody.stmts.add(returnStmt);

        //Create event filter definition
        BVarSymbol nextProcessInvokableTypeVarSymbol = nextProcessVarSymbolStack.pop();
        List<BAttachedFunction> attachedFunctionsList = ((BObjectTypeSymbol)
                (nextProcessInvokableTypeVarSymbol).type.tsymbol).attachedFuncs;
        BInvokableSymbol nextProcessInvokableSymbol = null;
        for (BAttachedFunction attachedFunction : attachedFunctionsList) {
            if (attachedFunction.funcName.toString().equals(NEXT_PROCESS_METHOD_NAME)) {
                nextProcessInvokableSymbol = attachedFunction.symbol;
            }
        }

        BLangSimpleVarRef nextProcessSimpleVarRef = ASTBuilderUtil.createVariableRef(where.pos,
                nextProcessInvokableTypeVarSymbol);
        BLangFieldBasedAccess nextProcessMethodAccess = (BLangFieldBasedAccess)
                TreeBuilder.createFieldBasedAccessNode();
        nextProcessMethodAccess.expr = nextProcessSimpleVarRef;
        nextProcessMethodAccess.symbol = nextProcessInvokableSymbol;
        nextProcessMethodAccess.type = nextProcessInvokableSymbol.type;
        nextProcessMethodAccess.pos = where.pos;
        nextProcessMethodAccess.field = ASTBuilderUtil.createIdentifier(where.pos, NEXT_PROCESS_METHOD_NAME);

        BInvokableSymbol filterInvokableSymbol = (BInvokableSymbol) symResolver.
                resolvePkgSymbol(where.pos, env, names.fromString(STREAMS_STDLIB_PACKAGE_NAME)).
                scope.lookup(new Name("createFilter")).symbol;

        BType filterInvokableType = filterInvokableSymbol.type.getReturnType();
        BVarSymbol filterInvokableTypeVarSymbol = new BVarSymbol(0,
                new Name(getVariableName(FILTER_FUNC_REFERENCE)), filterInvokableSymbol.pkgID,
                filterInvokableType, env.scope.owner);
        nextProcessVarSymbolStack.push(filterInvokableTypeVarSymbol);

        List<BLangExpression> args = new ArrayList<>();
        args.add(nextProcessMethodAccess);
        args.add(filterLambdaFunction);

        BLangInvocation filterMethodInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(where.pos, filterInvokableSymbol, args,
                        symResolver);
        filterMethodInvocation.argExprs = args;
        BLangVariable filterInvokableTypeVariable = ASTBuilderUtil.
                createVariable(where.pos, getVariableName(FILTER_FUNC_REFERENCE),
                        filterInvokableType, filterMethodInvocation, filterInvokableTypeVarSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(where.pos, "Filter");
        userDefinedType.type = filterInvokableType;
        filterInvokableTypeVariable.setTypeNode(userDefinedType);
        BLangVariableDef filterInvokableTypeVariableDef = ASTBuilderUtil.createVariableDef(where.pos,
                filterInvokableTypeVariable);
        stmts.add(filterInvokableTypeVariableDef);
    }

    //------------------------------------- Methods required for filter -----------------------------------

    @Override
    public void visit(BLangBinaryExpr binaryExpr) {
        final BLangBinaryExpr bLangBinaryExpr = (BLangBinaryExpr) TreeBuilder.createBinaryExpressionNode();
        bLangBinaryExpr.pos = binaryExpr.pos;
        bLangBinaryExpr.type = symTable.booleanType;
        bLangBinaryExpr.opKind = (binaryExpr).getOperatorKind();
        binaryExpr.getLeftExpression().accept(this);
        bLangBinaryExpr.lhsExpr = filterConditionalExpression;
        binaryExpr.getRightExpression().accept(this);
        bLangBinaryExpr.rhsExpr = filterConditionalExpression;
        bLangBinaryExpr.opSymbol = binaryExpr.opSymbol;
        filterConditionalExpression = bLangBinaryExpr;
    }

    @Override
    public void visit(BLangFieldBasedAccess fieldAccessExpr) {
        BLangSimpleVarRef varRef = ASTBuilderUtil.createVariableRef(fieldAccessExpr.pos,
                filterTypeCastedVariable.symbol);
        filterConditionalExpression = ASTBuilderUtil.createFieldAccessExpr(varRef,
                fieldAccessExpr.field);
        ((BLangFieldBasedAccess) filterConditionalExpression).symbol = fieldAccessExpr.symbol;
        (filterConditionalExpression).type = fieldAccessExpr.type;
    }

    @Override
    public void visit(BLangLiteral literalExpr) {
        filterConditionalExpression = literalExpr;
    }

    //----------------------------------------- Util Methods ---------------------------------------------------------

    private String getFunctionName(String name) {
        return name + lambdaFunctionCount++;
    }

    private String getVariableName(String name) {
        return name + lambdaFunctionCount;
    }

    private void defineFunction(BLangFunction funcNode, BLangPackage targetPkg) {
        final BPackageSymbol packageSymbol = targetPkg.symbol;
        final SymbolEnv packageEnv = this.symTable.pkgEnvMap.get(packageSymbol);
        symbolEnter.defineNode(funcNode, packageEnv);
        packageEnv.enclPkg.functions.add(funcNode);
        packageEnv.enclPkg.topLevelNodes.add(funcNode);
    }

    private BLangVariable createAnyTypeVariable(String variableName, DiagnosticPos pos, SymbolEnv env) {
        BType varType = this.symTable.anyType;
        BVarSymbol varSymbol = new BVarSymbol(0, new Name(variableName),
                varType.tsymbol.pkgID, varType, env.scope.owner);

        BLangVariable anyTypeVariable = ASTBuilderUtil.createVariable(pos, variableName,
                varType, null, varSymbol);
        anyTypeVariable.typeNode = ASTBuilderUtil.createTypeNode(varType);
        return anyTypeVariable;
    }

    private BLangVariable createStreamEventTypeVariable(String variableName, DiagnosticPos pos, SymbolEnv env) {

        BRecordTypeSymbol recordTypeSymbol = (BRecordTypeSymbol) symResolver.
                resolvePkgSymbol(pos, env, names.fromString(STREAMS_STDLIB_PACKAGE_NAME)).
                scope.lookup(new Name(STREAM_EVENT_OBJECT_NAME)).symbol;

        BType varType = recordTypeSymbol.type;
        BVarSymbol varSymbol = new BVarSymbol(0, new Name(variableName),
                varType.tsymbol.pkgID, varType, env.scope.owner);

        BLangVariable streamEventTypeVariable = ASTBuilderUtil.createVariable(pos, variableName,
                varType, null, varSymbol);

        BLangUserDefinedType userDefinedType = (BLangUserDefinedType) TreeBuilder.createUserDefinedTypeNode();
        userDefinedType.typeName = ASTBuilderUtil.createIdentifier(pos, STREAM_EVENT_OBJECT_NAME);
        userDefinedType.type = varType;
        streamEventTypeVariable.setTypeNode(userDefinedType);

        return streamEventTypeVariable;
    }

    private static BLangExpression generateConversionExpr(BLangExpression varRef, BType target,
                                                          SymbolResolver symResolver) {

        // Box value using cast expression.
        final BLangTypeConversionExpr conversion = (BLangTypeConversionExpr) TreeBuilder.createTypeConversionNode();
        conversion.pos = varRef.pos;
        conversion.expr = varRef;
        conversion.type = target;
        conversion.targetType = target;
        conversion.conversionSymbol = (BConversionOperatorSymbol) symResolver.resolveConversionOperator(varRef.type,
                target);
        return conversion;
    }

    private static BVarSymbol getOutputEventFieldSymbol(BLangVariable outputEventTypeVariable, String fieldName) {
        List<BField> recordTypeFieldList = ((BRecordType) outputEventTypeVariable.type).fields;
        for (BField field : recordTypeFieldList) {
            if (field.getName().value.equals(fieldName)) {
                return field.symbol;
            }
        }
        return null;
    }
}
