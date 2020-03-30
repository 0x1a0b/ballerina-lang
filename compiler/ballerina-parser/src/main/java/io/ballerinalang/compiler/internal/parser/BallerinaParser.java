/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerinalang.compiler.internal.parser;

import io.ballerinalang.compiler.internal.parser.BallerinaParserErrorHandler.Action;
import io.ballerinalang.compiler.internal.parser.BallerinaParserErrorHandler.Solution;
import io.ballerinalang.compiler.internal.parser.tree.STCheckExpression;
import io.ballerinalang.compiler.internal.parser.tree.STMissingToken;
import io.ballerinalang.compiler.internal.parser.tree.STModuleNamePart;
import io.ballerinalang.compiler.internal.parser.tree.STNode;
import io.ballerinalang.compiler.internal.parser.tree.STNodeFactory;
import io.ballerinalang.compiler.internal.parser.tree.STToken;
import io.ballerinalang.compiler.internal.parser.tree.SyntaxKind;

import java.util.ArrayList;
import java.util.List;

/**
 * A LL(k) recursive-descent parser for ballerina.
 * 
 * @since 1.2.0
 */
public class BallerinaParser {

    private final BallerinaParserErrorHandler errorHandler;
    private final AbstractTokenReader tokenReader;

    // TODO: Remove this.
    private ParserRuleContext currentParamKind = ParserRuleContext.REQUIRED_PARAM;

    protected BallerinaParser(AbstractTokenReader tokenReader) {
        this.tokenReader = tokenReader;
        this.errorHandler = new BallerinaParserErrorHandler(tokenReader, this);
    }

    /**
     * Start parsing the given input.
     * 
     * @return Parsed node
     */
    public STNode parse() {
        return parseCompUnit();
    }

    /**
     * Resume the parsing from the given context.
     * 
     * @param context Context to resume parsing
     * @param parsedNodes Parsed that requires to continue parsing the given parser context
     * @return Parsed node
     */
    public STNode resumeParsing(ParserRuleContext context, STNode... parsedNodes) {
        switch (context) {
            case COMP_UNIT:
                return parseCompUnit();
            case EXTERNAL_FUNC_BODY:
                return parseExternalFunctionBody();
            case FUNC_BODY:
                return parseFunctionBody();
            case OPEN_BRACE:
                return parseOpenBrace();
            case CLOSE_BRACE:
                return parseCloseBrace();
            case FUNC_NAME:
                return parseFunctionName();
            case OPEN_PARENTHESIS:
                return parseOpenParenthesis();
            case PARAM_LIST:
                return parseParamList();
            case RETURN_TYPE_DESCRIPTOR:
                return parseReturnTypeDescriptor();
            case SIMPLE_TYPE_DESCRIPTOR:
                return parseTypeDescriptor();
            case ASSIGN_OP:
                return parseAssignOp();
            case ANNOTATION_ATTACHMENT:
            case EXTERNAL_KEYWORD:
                return parseExternalKeyword();
            case FUNC_BODY_BLOCK:
                return parseFunctionBodyBlock();
            case SEMICOLON:
                return parseSemicolon();
            case CLOSE_PARENTHESIS:
                return parseCloseParenthesis();
            case VARIABLE_NAME:
                return parseVariableName();
            case EXPRESSION:
                return parseExpression();
            case STATEMENT:
                return parseStatement();
            case VAR_DECL_STMT:
                return parseVariableDeclStmt();
            case ASSIGNMENT_STMT:
                return parseAssignmentStmt();
            case EXPRESSION_RHS:
                return parseExpressionRhs(parsedNodes[0]);
            case AFTER_PARAMETER_TYPE:
                return parseAfterParamType(parsedNodes[0], parsedNodes[1]);
            case PARAMETER_RHS:
                return parseParameterRhs(parsedNodes[0], parsedNodes[1], parsedNodes[2]);
            case TOP_LEVEL_NODE_WITHOUT_MODIFIER:
                return parseTopLevelNode(parsedNodes[0]);
            case TOP_LEVEL_NODE:
                return parseTopLevel();
            case STATEMENT_START_IDENTIFIER:
                return parseStatementStartIdentifier();
            case VAR_DECL_STMT_RHS:
                return parseVarDeclRhs(parsedNodes[0], parsedNodes[1]);
            case ASSIGNMENT_OR_VAR_DECL_STMT:
                return parseAssignmentOrVarDecl();
            case ASSIGNMENT_OR_VAR_DECL_STMT_RHS:
                return parseAssignmentOrVarDeclRhs(parsedNodes[0]);
            case TYPE_REFERENCE:
                return parseTypeReference();
            case FIELD_DESCRIPTOR_RHS:
                return parseFieldDescriptorRhs(parsedNodes[0], parsedNodes[1]);
            case NAMED_OR_POSITIONAL_ARG_RHS:
                return parseNamedOrPositionalArg(parsedNodes[0]);
            case RECORD_BODY_END:
                return parseRecordBodyCloseDelimiter();
            case RECORD_BODY_START:
                return parseRecordBodyStartDelimiter();
            case TYPE_DESCRIPTOR:
                return parseTypeDescriptor();
            case OBJECT_MEMBER:
                return parseObjectMember();
            case OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY:
                return parseObjectMethodOrField(parsedNodes[0]);
            case OBJECT_FIELD_RHS:
                return parseObjectFieldRhs(parsedNodes[0], parsedNodes[1], parsedNodes[2]);
            case OBJECT_TYPE_FIRST_QUALIFIER:
                return parseObjectTypeQualifiers();
            case OBJECT_TYPE_SECOND_QUALIFIER:
                return parseObjectTypeSecondQualifier(parsedNodes[0]);
            case OBJECT_KEYWORD:
                return parseObjectKeyword();
            case TYPE_NAME:
                return parseTypeName();
            case IF_KEYWORD:
                return parseIfKeyword();
            case ELSE_KEYWORD:
                return parseElseKeyword();
            case ELSE_BODY:
                return parseElseBody();
            case WHILE_KEYWORD:
                return parseWhileKeyword();
            case BOOLEAN_LITERAL:
                return parseBooleanLiteral();
            case FUNC_DEFINITION:
            case REQUIRED_PARAM:
            default:
                throw new IllegalStateException("Cannot re-parse rule: " + context);
        }
    }

    /**
     * Start parsing the input from a given context. Supported starting points are:
     * <ul>
     * <li>Module part (a file)</li>
     * <li>Top level node</li>
     * <li>Statement</li>
     * <li>Expression</li>
     * </ul>
     * 
     * @param context Context to start parsing
     * @return Parsed node
     */
    public STNode parse(ParserRuleContext context) {
        switch (context) {
            case COMP_UNIT:
                return parseCompUnit();
            case TOP_LEVEL_NODE:
                startContext(ParserRuleContext.COMP_UNIT);
                return parseTopLevel();
            case STATEMENT:
                startContext(ParserRuleContext.COMP_UNIT);
                startContext(ParserRuleContext.FUNC_DEFINITION);
                startContext(ParserRuleContext.FUNC_BODY_BLOCK);
                return parseStatement();
            case EXPRESSION:
                startContext(ParserRuleContext.COMP_UNIT);
                startContext(ParserRuleContext.FUNC_DEFINITION);
                startContext(ParserRuleContext.FUNC_BODY_BLOCK);
                startContext(ParserRuleContext.STATEMENT);
                return parseExpression();
            default:
                throw new UnsupportedOperationException("Cannot start parsing from: " + context);
        }
    }

    /*
     * Private methods
     */

    private STToken peek() {
        return this.tokenReader.peek();
    }

    private STToken peek(int k) {
        return this.tokenReader.peek(k);
    }

    private STToken consume() {
        return this.tokenReader.read();
    }

    private Solution recover(STToken token, ParserRuleContext currentCtx, STNode... parsedNodes) {
        return this.errorHandler.recover(currentCtx, token, parsedNodes);
    }

    private void startContext(ParserRuleContext context) {
        this.errorHandler.startContext(context);
    }

    private void endContext() {
        this.errorHandler.endContext();
    }

    /**
     * Switch the current context to the provided one. This will replace the
     * existing context.
     * 
     * @param context Context to switch to.
     */
    private void switchContext(ParserRuleContext context) {
        this.errorHandler.switchContext(context);
    }

    /**
     * Parse a given input and returns the AST. Starts parsing from the top of a compilation unit.
     * 
     * @return Parsed node
     */
    private STNode parseCompUnit() {
        startContext(ParserRuleContext.COMP_UNIT);
        STToken token = peek();
        List<STNode> otherDecls = new ArrayList<>();
        List<STNode> importDecls = new ArrayList<>();
        while (token.kind != SyntaxKind.EOF_TOKEN) {
            otherDecls.add(parseTopLevelNode(token.kind));
            token = peek();
        }

        STToken eof = consume();
        endContext();

        return STNodeFactory.createModulePart(STNodeFactory.createNodeList(importDecls),
                STNodeFactory.createNodeList(otherDecls), eof);
    }

    /**
     * Parse top level node having an optional modifier preceding it.
     * 
     * @return Parsed node
     */
    private STNode parseTopLevel() {
        STToken token = peek();
        return parseTopLevelNode(token.kind);
    }

    /**
     * Parse top level node having an optional modifier preceding it, given the next token kind.
     * 
     * @param tokenKind Next token kind
     * @return Parsed node
     */
    protected STNode parseTopLevelNode(SyntaxKind tokenKind) {
        STNode modifier;
        switch (tokenKind) {
            case PUBLIC_KEYWORD:
                modifier = parseModifier();
                break;
            case FUNCTION_KEYWORD:
            case TYPE_KEYWORD:
                modifier = STNodeFactory.createEmptyNode();
                break;
            case EOF_TOKEN:
                return consume();
            case IMPORT_KEYWORD:
                return parseImportDecl();
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.TOP_LEVEL_NODE);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseTopLevelNode(solution.tokenKind);
        }

        return parseTopLevelNode(modifier);
    }

    /**
     * Parse import declaration.
     * 
     * @return Parsed node
     */
    private STNode parseImportDecl() {
        STNode importKeyword = parseImportKeyword();
        STNode identifier = parseIdentifier();
        return parseImportDecl(importKeyword, identifier);
    }

    /**
     * Parse import keyword.
     * 
     * @return Parsed node
     */
    private STNode parseImportKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IMPORT_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.IMPORT_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse identifier.
     * 
     * @return Parsed node
     */
    private STNode parseIdentifier() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, null);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse dot or slash token.
     * 
     * @return Parsed node
     */
    private STNode parseImportDecl(STNode importKeyword, STNode identifier) {
        STToken token = peek();
        STNode orgName;
        STNode moduleName;
        switch (token.kind) {
            case DOT_TOKEN:
                orgName = STNodeFactory.createEmptyNode();
                moduleName = parseModuleName(identifier);
                break;
            case SLASH_TOKEN:
                STNode slash = parseSlashToken();
                orgName = STNodeFactory.createOrgName(identifier, slash);
                moduleName = parseModuleName();
                break;
            case VERSION_KEYWORD:
            case AS_KEYWORD:
            case SEMICOLON_TOKEN:
                orgName = STNodeFactory.createEmptyNode();
                moduleName = identifier;
                break;
            default:
                Solution solution = recover(peek(), null, importKeyword, identifier);
                return solution.recoveredNode;
        }

        STNode version = parseVersion();
        STNode alias = parseAlias();
        STNode semicolon = parseSemicolon();
        return STNodeFactory.createImportDecl(importKeyword, orgName, moduleName, version, alias, semicolon);
    }

    private STNode parseSlashToken() {
        STToken token = peek();
        if (token.kind == SyntaxKind.SLASH_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.SLASH);
            return sol.recoveredNode;
        }
    }

    private STNode parseDotToken() {
        STToken nextToken = peek();
        return parseDotToken(nextToken.kind);
    }

    private STNode parseDotToken(SyntaxKind tokenKind) {
        if (tokenKind == SyntaxKind.DOT_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(peek(), ParserRuleContext.DOT);
            return sol.recoveredNode;
        }
    }

    private STNode parseModuleName() {
        STNode moduleNameStart = parseIdentifier();
        return parseModuleName(moduleNameStart);
    }

    /**
     * @param moduleNameStart Starting identifier of the module name
     * @return
     */
    private STNode parseModuleName(STNode moduleNameStart) {
        List<STNode> moduleNameParts = new ArrayList<>();
        STNode identifier;
        STNode dotToken;
        STNode nextToken = peek();
        while (!isEndOfImportModuleName(nextToken.kind)) {
            dotToken = parseDotToken(nextToken.kind);
            identifier = parseIdentifier();
            STNode moduleNamePart = STNodeFactory.createModuleNamePart(dotToken, identifier);
            moduleNameParts.add(moduleNamePart);
            nextToken = peek();
        }

        return STNodeFactory.createModuleName(moduleNameStart, STNodeFactory.createNodeList(moduleNameParts));
    }

    private boolean isEndOfImportModuleName(SyntaxKind nextTokenKind) {
        switch (nextTokenKind) {
            case VERSION_KEYWORD:
            case AS_KEYWORD:
                return true;
            default:
                return isEndOfImportDecl(nextTokenKind);
        }
    }

    private boolean isEndOfImportDecl(SyntaxKind nextTokenKind) {
        switch (nextTokenKind) {
            case SEMICOLON_TOKEN:
            case PUBLIC_KEYWORD:
            case FUNCTION_KEYWORD:
            case TYPE_KEYWORD:
            case ABSTRACT_KEYWORD:
            case CONST_KEYWORD:
                return true;
            default:
                return false;
        }
    }

    private STNode parseVersion() {
        STToken nextToken = peek();
        return parseVersion(nextToken.kind);
    }

    private STNode parseVersion(SyntaxKind nextTokenKind) {
        switch (nextTokenKind) {
            case VERSION_KEYWORD:
                STNode versionKeyword = parseVersionKeywrod();
                STNode semVer = parseSemVer();
                return STNodeFactory.createImportVersion(versionKeyword, semVer);
            case AS_KEYWORD:
            case SEMICOLON_TOKEN:
                return STNodeFactory.createEmptyNode();
            default:
                if (isEndOfImportDecl(nextTokenKind)) {
                    return STNodeFactory.createEmptyNode();
                }

                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.IMPORT_VERSION);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseVersion(solution.tokenKind);
        }

    }

    private STNode parseVersionKeywrod() {
        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.VERSION_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(peek(), ParserRuleContext.VERSION_KEYWORD);
            return sol.recoveredNode;
        }
    }

    private STNode parseSemVer() {
        this.tokenReader.switchMode(ParserMode.SEM_VAR);
        STToken nextToken = peek();
        STNode semVar = parseSemVer(nextToken.kind);
        this.tokenReader.resetMode();
        return semVar;
    }

    private STNode parseSemVer(SyntaxKind nextTokenKind) {
        STNode majorVersion;
        switch (nextTokenKind) {
            case DECIMAL_INTEGER_LITERAL:
                majorVersion = parseDecimalIntLiteral();
                break;
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.SEM_VER);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseSemVer(solution.tokenKind);
        }

        List<STNode> versionParts = new ArrayList<>();
        versionParts.add(majorVersion);

        STNode minorVersion = parseSubVersion();
        if (minorVersion != null) {
            versionParts.add(minorVersion);

            STNode patchVersion = parseSubVersion();
            if (patchVersion != null) {
                versionParts.add(patchVersion);
            }
        }

        return STNodeFactory.createNodeList(versionParts);

    }

    private STNode parseDecimalIntLiteral() {
        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.DECIMAL_INTEGER_LITERAL) {
            return consume();
        } else {
            Solution sol = recover(peek(), ParserRuleContext.DECIMAL_INTEGER_LITERAL);
            return sol.recoveredNode;
        }
    }

    private STNode parseSubVersion() {
        STToken nextToken = peek();
        return parseSubVersion(nextToken.kind);
    }

    private STNode parseSubVersion(SyntaxKind nextTokenKind) {
        switch (nextTokenKind) {
            case AS_KEYWORD:
            case SEMICOLON_TOKEN:
                return null;
            case DOT_TOKEN:
                STNode leadingDot = parseDotToken();
                STNode versionNumber = parseDecimalIntLiteral();
                return STNodeFactory.createVersionPart(leadingDot, versionNumber);
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.IMPORT_VERSION);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseSemVer(solution.tokenKind);
        }
    }

    private STNode parseAlias() {
        STToken token = peek();
        return parseAlias(token.kind);
    }

    private STNode parseAlias(SyntaxKind nextTokenKind) {
        switch (nextTokenKind) {
            case AS_KEYWORD:
                STNode asKeyword = parseAsKeyword();
                STNode importPrefix = parseImportPrefix();
                return STNodeFactory.createImportAlias(asKeyword, importPrefix);
            case SEMICOLON_TOKEN:
                return STNodeFactory.createEmptyNode();
            default:
                if (isEndOfImportDecl(nextTokenKind)) {
                    return STNodeFactory.createEmptyNode();
                }

                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.IMPORT_ALIAS);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseVersion(solution.tokenKind);
        }
    }

    private STNode parseAsKeyword() {
        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.AS_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(peek(), ParserRuleContext.AS_KEYWORD);
            return sol.recoveredNode;
        }
    }

    private STNode parseImportPrefix() {
        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(peek(), ParserRuleContext.IMPORT_PREFIX);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse top level node, given the modifier that precedes it.
     * 
     * @param modifier Modifier that precedes the top level node
     * @return Parsed node
     */
    private STNode parseTopLevelNode(STNode modifier) {
        STToken token = peek();
        return parseTopLevelNode(token.kind, modifier);
    }

    /**
     * Parse top level node given the next token kind and the modifier that precedes it.
     * 
     * @param tokenKind Next token kind
     * @param modifier Modifier that precedes the top level node
     * @return Parsed top-level node
     */
    private STNode parseTopLevelNode(SyntaxKind tokenKind, STNode modifier) {
        switch (tokenKind) {
            case FUNCTION_KEYWORD:
                return parseFunctionDefinition(modifier);
            case TYPE_KEYWORD:
                return parseModuleTypeDefinition(modifier);
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.TOP_LEVEL_NODE_WITHOUT_MODIFIER, modifier);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseTopLevelNode(solution.tokenKind, modifier);
        }
    }

    /**
     * Parse access modifiers.
     * 
     * @return Parsed node
     */
    private STNode parseModifier() {
        STToken token = peek();
        if (token.kind == SyntaxKind.PUBLIC_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.PUBLIC_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse function definition. A function definition has the following structure.
     * </p>
     * <code>
     * function-defn := FUNCTION identifier function-signature function-body
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseFunctionDefinition(STNode visibilityQualifier) {
        startContext(ParserRuleContext.FUNC_DEFINITION);

        STNode functionKeyword = parseFunctionKeyword();
        STNode name = parseFunctionName();
        STNode openParenthesis = parseOpenParenthesis();
        STNode parameters = parseParamList();
        STNode closeParenthesis = parseCloseParenthesis();
        STNode returnTypeDesc = parseReturnTypeDescriptor();
        STNode body = parseFunctionBody();

        endContext();
        return STNodeFactory.createFunctionDefinition(visibilityQualifier, functionKeyword, name, openParenthesis,
                parameters, closeParenthesis, returnTypeDesc, body);
    }

    /**
     * Parse function keyword. Need to validate the token before consuming,
     * since we can reach here while recovering.
     * 
     * @return Parsed node
     */
    private STNode parseFunctionKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.FUNCTION_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.FUNCTION_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse function name.
     * 
     * @return Parsed node
     */
    private STNode parseFunctionName() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.FUNC_NAME);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse open parenthesis.
     * 
     * @return Parsed node
     */
    private STNode parseOpenParenthesis() {
        STToken token = peek();
        if (token.kind == SyntaxKind.OPEN_PAREN_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.OPEN_PARENTHESIS);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse close parenthesis.
     * 
     * @return Parsed node
     */
    private STNode parseCloseParenthesis() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CLOSE_PAREN_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CLOSE_PARENTHESIS);
            return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse parameter list.
     * </p>
     * <code>
     * param-list := required-params [, defaultable-params] [, rest-param]
     *     <br/>&nbsp;| defaultable-params [, rest-param]
     *     <br/>&nbsp;| [rest-param]
     * <br/><br/>
     * required-params := required-param (, required-param)*
     * <br/><br/>
     * required-param := [annots] [public] type-descriptor [param-name]
     * <br/><br/>
     * defaultable-params := defaultable-param (, defaultable-param)*
     * <br/><br/>
     * defaultable-param := [annots] [public] type-descriptor [param-name] default-value
     * <br/><br/>
     * rest-param := [annots] type-descriptor ... [param-name]
     * <br/><br/>
     * param-name := identifier
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseParamList() {
        startContext(ParserRuleContext.PARAM_LIST);
        ArrayList<STNode> paramsList = new ArrayList<>();

        STToken token = peek();
        if (isEndOfParametersList(token.kind)) {
            STNode params = STNodeFactory.createNodeList(paramsList);
            endContext();
            return params;
        }

        // Parse the first parameter. Comma precedes the first parameter doesn't exist.
        STNode startingComma = STNodeFactory.createEmptyNode();
        this.currentParamKind = ParserRuleContext.REQUIRED_PARAM;
        paramsList.add(parseParameter(startingComma));

        // Parse follow-up parameters.
        token = peek();
        while (!isEndOfParametersList(token.kind)) {
            STNode leadingComma = parseComma();
            STNode param = parseParameter(leadingComma);
            paramsList.add(param);
            token = peek();
        }

        STNode params = STNodeFactory.createNodeList(paramsList);
        endContext();
        return params;
    }

    /**
     * Parse a single parameter. Parameter can be a required parameter, a defaultable
     * parameter, or a rest parameter.
     * 
     * @param leadingComma Comma that occurs before the param
     * @return Parsed node
     */
    private STNode parseParameter(STNode leadingComma) {
        STToken token = peek();
        if (this.currentParamKind == ParserRuleContext.REST_PARAM) {
            // This is an erroneous scenario, where there are more parameters after
            // the rest parameter. Log an error, and continue the remainder of the
            // parameters by removing the order restriction.

            // TODO: mark the node as erroneous
            this.errorHandler.reportInvalidNode(token, "cannot have more parameters after the rest-parameter");
            startContext(ParserRuleContext.REQUIRED_PARAM);
        } else {
            startContext(this.currentParamKind);
        }

        if (token.kind == SyntaxKind.PUBLIC_KEYWORD) {
            parseModifier();
            token = peek();
        }

        STNode type = parseTypeDescriptor(token.kind);
        STNode param = parseAfterParamType(leadingComma, type);
        endContext();
        return param;
    }

    private STNode parseAfterParamType(STNode leadingComma, STNode type) {
        STToken token = peek();
        return parseAfterParamType(token.kind, leadingComma, type);
    }

    private STNode parseAfterParamType(SyntaxKind tokenKind, STNode leadingComma, STNode type) {
        switch (tokenKind) {
            case ELLIPSIS_TOKEN:
                this.currentParamKind = ParserRuleContext.REST_PARAM;
                switchContext(ParserRuleContext.REST_PARAM);
                STNode ellipsis = parseEllipsis();
                STNode paramName = parseVariableName();
                return STNodeFactory.createRestParameter(SyntaxKind.PARAMETER, leadingComma, type, ellipsis, paramName);
            case IDENTIFIER_TOKEN:
                paramName = parseVariableName();
                return parseParameterRhs(leadingComma, type, paramName);
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.AFTER_PARAMETER_TYPE, leadingComma, type);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseAfterParamType(solution.tokenKind, leadingComma, type);
        }
    }

    /**
     * Parse ellipsis.
     * 
     * @return Parsed node
     */
    private STNode parseEllipsis() {
        STToken token = peek();
        if (token.kind == SyntaxKind.ELLIPSIS_TOKEN) {
            return consume(); // parse '...'
        } else {
            Solution sol = recover(token, ParserRuleContext.ELLIPSIS);
            return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse the right hand side of a required/defaultable parameter.
     * </p>
     * <code>parameter-rhs := [= expression]</code>
     * 
     * @return Parsed parameter node
     */
    private STNode parseParameterRhs(STNode leadingComma, STNode type, STNode paramName) {
        STToken token = peek();
        return parseParameterRhs(token.kind, leadingComma, type, paramName);
    }

    /**
     * Parse the right hand side of a required/defaultable parameter, given the next token kind.
     * 
     * @param tokenKind Next token kind
     * @return Parsed parameter node
     */
    private STNode parseParameterRhs(SyntaxKind tokenKind, STNode leadingComma, STNode type, STNode paramName) {
        // Required parameters
        if (isEndOfParameter(tokenKind)) {
            if (this.currentParamKind == ParserRuleContext.DEFAULTABLE_PARAM) {
                // This is an erroneous scenario, where a required parameters comes after
                // a defaulatble parameter. Log an error, and continue.

                // TODO: mark the node as erroneous
                // TODO: Fix the line number in the error
                this.errorHandler.reportInvalidNode(peek(),
                        "cannot have a required parameter after a defaultable parameter");
            }

            // TODO: add access modifier
            STNode visibilityQualifier = STNodeFactory.createEmptyNode();
            return STNodeFactory.createRequiredParameter(SyntaxKind.PARAMETER, leadingComma, visibilityQualifier, type,
                    paramName);
        } else if (tokenKind == SyntaxKind.EQUAL_TOKEN) {

            // If we were processing required params so far and found a defualtable
            // parameter, then switch the context to defaultable params.
            if (this.currentParamKind == ParserRuleContext.REQUIRED_PARAM) {
                this.currentParamKind = ParserRuleContext.DEFAULTABLE_PARAM;
                switchContext(ParserRuleContext.DEFAULTABLE_PARAM);
            }

            // Defaultable parameters
            STNode equal = parseAssignOp();
            STNode expr = parseExpression();
            STNode visibilityQualifier = STNodeFactory.createEmptyNode();
            return STNodeFactory.createDefaultableParameter(SyntaxKind.PARAMETER, leadingComma, visibilityQualifier,
                    type, paramName, equal, expr);
        } else {
            STToken token = peek();
            Solution solution = recover(token, ParserRuleContext.PARAMETER_RHS, leadingComma, type, paramName);

            // If the parser recovered by inserting a token, then try to re-parse the same
            // rule with the inserted token. This is done to pick the correct branch
            // to continue the parsing.
            if (solution.action == Action.REMOVE) {
                return solution.recoveredNode;
            }

            return parseParameterRhs(solution.tokenKind, leadingComma, type, paramName);
        }
    }

    /**
     * Parse comma.
     * 
     * @return Parsed node
     */
    private STNode parseComma() {
        STToken token = peek();
        if (token.kind == SyntaxKind.COMMA_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.COMMA);
            return sol.recoveredNode;
        }
    }

    /**
     * Check whether the given token is an end of a parameter.
     * 
     * @param tokenKind Next token kind
     * @return <code>true</code> if the token represents an end of a parameter. <code>false</code> otherwise
     */
    private boolean isEndOfParameter(SyntaxKind tokenKind) {
        switch (tokenKind) {
            case CLOSE_BRACE_TOKEN:
            case CLOSE_PAREN_TOKEN:
            case CLOSE_BRACKET_TOKEN:
            case SEMICOLON_TOKEN:
            case COMMA_TOKEN:
            case PUBLIC_KEYWORD:
            case FUNCTION_KEYWORD:
            case EOF_TOKEN:
            case RETURNS_KEYWORD:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check whether the given token is an end of a parameter-list.
     * 
     * @param tokenKind Next token kind
     * @return <code>true</code> if the token represents an end of a parameter-list. <code>false</code> otherwise
     */
    private boolean isEndOfParametersList(SyntaxKind tokenKind) {
        switch (tokenKind) {
            case CLOSE_BRACE_TOKEN:
            case CLOSE_PAREN_TOKEN:
            case CLOSE_BRACKET_TOKEN:
            case OPEN_BRACE_TOKEN:
            case SEMICOLON_TOKEN:
            case PUBLIC_KEYWORD:
            case FUNCTION_KEYWORD:
            case EOF_TOKEN:
            case RETURNS_KEYWORD:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse return type descriptor of a function. A return type descriptor has the following structure.
     * 
     * <code>return-type-descriptor := [ returns annots type-descriptor ]</code>
     * 
     * @return Parsed node
     */
    private STNode parseReturnTypeDescriptor() {
        startContext(ParserRuleContext.RETURN_TYPE_DESCRIPTOR);

        // If the return type is not present, simply return
        STToken token = peek();
        if (token.kind != SyntaxKind.RETURNS_KEYWORD) {
            endContext();
            return STNodeFactory.createEmptyNode();
        }

        STNode returnsKeyword = consume();
        STNode annot = parseAnnotations();
        STNode type = parseTypeDescriptor();

        endContext();
        return STNodeFactory.createReturnTypeDescriptor(returnsKeyword, annot, type);
    }

    /**
     * <p>
     * Parse a type descriptor. A type descriptor has the following structure.
     * </p>
     * <code>type-descriptor :=
     *      &nbsp;simple-type-descriptor<br/>
     *      &nbsp;| structured-type-descriptor<br/>
     *      &nbsp;| behavioral-type-descriptor<br/>
     *      &nbsp;| singleton-type-descriptor<br/>
     *      &nbsp;| union-type-descriptor<br/>
     *      &nbsp;| optional-type-descriptor<br/>
     *      &nbsp;| any-type-descriptor<br/>
     *      &nbsp;| anydata-type-descriptor<br/>
     *      &nbsp;| byte-type-descriptor<br/>
     *      &nbsp;| json-type-descriptor<br/>
     *      &nbsp;| type-descriptor-reference<br/>
     *      &nbsp;| ( type-descriptor )
     * <br/>    
     * type-descriptor-reference := qualified-identifier</code>
     * 
     * @return Parsed node
     */
    private STNode parseTypeDescriptor() {
        STToken token = peek();
        return parseTypeDescriptor(token.kind);
    }

    /**
     * <p>
     * Parse a type descriptor, given the next token kind.
     * </p>
     * 
     * @param tokenKind Next token kind
     * @return Parsed node
     */
    private STNode parseTypeDescriptor(SyntaxKind tokenKind) {
        switch (tokenKind) {
            case SIMPLE_TYPE:
                // simple type descriptor
                return parseSimpleTypeDescriptor();
            case IDENTIFIER_TOKEN:
                return parseTypeReference();
            case RECORD_KEYWORD:
                // Record type descriptor
                return parseRecordTypeDescriptor();
            case OBJECT_KEYWORD:
            case ABSTRACT_KEYWORD:
            case CLIENT_KEYWORD:
                // Object type descriptor
                return parseObjectTypeDescriptor();
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.TYPE_DESCRIPTOR);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseTypeDescriptor(solution.tokenKind);
        }

        // TODO: only supports built-in/user-defined types. Add others.
    }

    /**
     * Parse simple type descriptor.
     * 
     * @return Parsed node
     */
    private STNode parseSimpleTypeDescriptor() {
        STToken node = peek();
        switch (node.kind) {
            case SIMPLE_TYPE:
                return consume();
            default:
                Solution sol = recover(peek(), ParserRuleContext.SIMPLE_TYPE_DESCRIPTOR);
                return sol.recoveredNode;
        }
    }

    /**
     * Parse annotation attachments.
     * 
     * @return Parsed node
     */
    private STNode parseAnnotations() {
        // TODO
        return STNodeFactory.createEmptyNode();
    }

    /**
     * <p>
     * Parse function body. A function body has the following structure.
     * </p>
     * <code>
     * function-body := function-body-block | external-function-body
     * external-function-body := = annots external ;
     * function-body-block := { [default-worker-init, named-worker-decl+] default-worker }
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseFunctionBody() {
        STToken token = peek();
        return parseFunctionBody(token.kind);
    }

    /**
     * Parse function body, given the next token kind.
     * 
     * @param tokenKind Next token kind
     * @return Parsed node
     */
    protected STNode parseFunctionBody(SyntaxKind tokenKind) {
        switch (tokenKind) {
            case EQUAL_TOKEN:
                return parseExternalFunctionBody();
            case OPEN_BRACE_TOKEN:
                return parseFunctionBodyBlock();
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.FUNC_BODY);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.

                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                // If the recovered token is not something that can be re-parsed,
                // then don't try to re-parse the same rule.
                if (solution.tokenKind == SyntaxKind.NONE) {
                    // TODO: check this again
                    return STNodeFactory.createMissingToken(solution.tokenKind);
                }

                return parseFunctionBody(solution.tokenKind);
        }
    }

    /**
     * <p>
     * Parse function body block. A function body block has the following structure.
     * </p>
     * 
     * <code>
     * function-body-block := { [default-worker-init, named-worker-decl+] default-worker }<br/>
     * default-worker-init := sequence-stmt<br/>
     * default-worker := sequence-stmt<br/>
     * named-worker-decl := worker worker-name return-type-descriptor { sequence-stmt }<br/>
     * worker-name := identifier<br/>
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseFunctionBodyBlock() {
        startContext(ParserRuleContext.FUNC_BODY_BLOCK);
        STNode openBrace = parseOpenBrace();
        STNode stmts = parseStatements(); // TODO: allow workers
        STNode closeBrace = parseCloseBrace();
        endContext();
        return STNodeFactory.createBlockStatement(openBrace, stmts, closeBrace);
    }

    /**
     * Check whether the given token is an end of a block.
     * 
     * @param tokenKind STToken to check
     * @return <code>true</code> if the token represents an end of a block. <code>false</code> otherwise
     */
    private boolean isEndOfBlockNode(SyntaxKind tokenKind) {
        switch (tokenKind) {
            case CLOSE_BRACE_TOKEN:
            case EOF_TOKEN:
            case CLOSE_BRACE_PIPE_TOKEN:
            case PUBLIC_KEYWORD:
            case FUNCTION_KEYWORD:
            case ELSE_KEYWORD:
                return true;
            default:
                return false;
        }
    }

    private boolean isEndOfRecordTypeNode(SyntaxKind nextTokenKind) {
        STToken nexNextToken = peek(2);
        return isEndOfBlockNode(nextTokenKind) || isEndOfBlockNode(nexNextToken.kind);
    }

    private boolean isEndOfObjectTypeNode(SyntaxKind tokenKind, SyntaxKind nextNextTokenKind) {
        switch (tokenKind) {
            case CLOSE_BRACE_TOKEN:
            case EOF_TOKEN:
            case CLOSE_BRACE_PIPE_TOKEN:
            case TYPE_KEYWORD:
                return true;
            default:
                switch (nextNextTokenKind) {
                    case CLOSE_BRACE_TOKEN:
                    case EOF_TOKEN:
                    case CLOSE_BRACE_PIPE_TOKEN:
                    case TYPE_KEYWORD:
                        return true;
                    default:
                        return false;
                }
        }
    }

    /**
     * Parse type reference or variable reference.
     * 
     * @return Parsed node
     */
    private STNode parseStatementStartIdentifier() {
        return parseQualifiedIdentifier(ParserRuleContext.STATEMENT_START_IDENTIFIER);
    }

    /**
     * Parse variable name.
     * 
     * @return Parsed node
     */
    private STNode parseVariableName() {
        STToken token = peek();
        return parseVariableName(token.kind);
    }

    /**
     * Parse variable name.
     * 
     * @return Parsed node
     */
    private STNode parseVariableName(SyntaxKind tokenKind) {
        if (tokenKind == SyntaxKind.IDENTIFIER_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(peek(), ParserRuleContext.VARIABLE_NAME);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse open brace.
     * 
     * @return Parsed node
     */
    private STNode parseOpenBrace() {
        STToken token = peek();
        if (token.kind == SyntaxKind.OPEN_BRACE_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.OPEN_BRACE);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse close brace.
     * 
     * @return Parsed node
     */
    private STNode parseCloseBrace() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CLOSE_BRACE_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CLOSE_BRACE);
            return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse external function body. An external function body has the following structure.
     * </p>
     * <code>
     * external-function-body := = annots external ;
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseExternalFunctionBody() {
        startContext(ParserRuleContext.EXTERNAL_FUNC_BODY);
        STNode assign = parseAssignOp();
        STNode annotation = parseAnnotations();
        STNode externalKeyword = parseExternalKeyword();
        STNode semicolon = parseSemicolon();

        endContext();
        return STNodeFactory.createExternalFunctionBody(SyntaxKind.EXTERNAL_FUNCTION_BODY, assign, annotation,
                externalKeyword, semicolon);
    }

    /**
     * Parse semicolon.
     * 
     * @return Parsed node
     */
    private STNode parseSemicolon() {
        STToken token = peek();
        if (token.kind == SyntaxKind.SEMICOLON_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.SEMICOLON);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse <code>external</code> keyword.
     * 
     * @return Parsed node
     */
    private STNode parseExternalKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.EXTERNAL_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.EXTERNAL_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /*
     * Operators
     */

    /**
     * Parse assign operator.
     * 
     * @return Parsed node
     */
    private STNode parseAssignOp() {
        STToken token = peek();
        if (token.kind == SyntaxKind.EQUAL_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.ASSIGN_OP);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse binary operator.
     * 
     * @return Parsed node
     */
    private STNode parseBinaryOperator() {
        STToken token = peek();
        if (isBinaryOperator(token.kind)) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.BINARY_OPERATOR);
            return sol.recoveredNode;
        }
    }

    /**
     * Check whether the given token kind is a binary operator.
     * 
     * @param kind STToken kind
     * @return <code>true</code> if the token kind refers to a binary operator. <code>false</code> otherwise
     */
    private boolean isBinaryOperator(SyntaxKind kind) {
        switch (kind) {
            case PLUS_TOKEN:
            case MINUS_TOKEN:
            case SLASH_TOKEN:
            case ASTERISK_TOKEN:
            case GT_TOKEN:
            case LT_TOKEN:
            case EQUAL_GT_TOKEN:
            case DOUBLE_EQUAL_TOKEN:
            case TRIPPLE_EQUAL_TOKEN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the precedence of a given operator.
     * 
     * @param binaryOpKind Operator kind
     * @return Precedence of the given operator
     */
    private OperatorPrecedence getOpPrecedence(SyntaxKind binaryOpKind) {
        switch (binaryOpKind) {
            case ASTERISK_TOKEN: // multiplication
            case SLASH_TOKEN: // division
                return OperatorPrecedence.MULTIPLICATIVE;
            case PLUS_TOKEN:
            case MINUS_TOKEN:
                return OperatorPrecedence.ADDITIVE;
            case GT_TOKEN:
            case LT_TOKEN:
                return OperatorPrecedence.BINARY_COMPARE;
            case DOT_TOKEN:
            case OPEN_BRACKET_TOKEN:
            case OPEN_PAREN_TOKEN:
                return OperatorPrecedence.MEMBER_ACCESS;
            default:
                throw new UnsupportedOperationException("Unsupported binary operator '" + binaryOpKind + "'");
        }
    }

    /**
     * <p>
     * Get the operator kind to insert during recovery, given the precedence level.
     * </p>
     * 
     * @param opPrecedenceLevel Precedence of the given operator
     * @return Kind of the operator to insert
     */
    private SyntaxKind getOperatorKindToInsert(OperatorPrecedence opPrecedenceLevel) {
        switch (opPrecedenceLevel) {
            case MULTIPLICATIVE:
                return SyntaxKind.ASTERISK_TOKEN;
            case ADDITIVE:
                return SyntaxKind.PLUS_TOKEN;
            case BINARY_COMPARE:
                return SyntaxKind.LT_TOKEN;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported operator precedence level'" + opPrecedenceLevel + "'");
        }
    }

    /**
     * <p>
     * Parse a module type definition.
     * </p>
     * <code>module-type-defn := metadata [public] type identifier type-descriptor ;</code>
     * 
     * @return Parsed node
     */
    private STNode parseModuleTypeDefinition(STNode modifier) {
        startContext(ParserRuleContext.MODULE_TYPE_DEFINITION);

        STNode typeKeyword = parseTypeKeyword();
        STNode typeName = parseTypeName();
        STNode typeDescriptor = parseTypeDescriptor();
        STNode comma = parseSemicolon();

        endContext();
        return STNodeFactory.createModuleTypeDefinition(modifier, typeKeyword, typeName, typeDescriptor, comma);
    }

    /**
     * Parse type keyword.
     * 
     * @return Parsed node
     */
    private STNode parseTypeKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.TYPE_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.TYPE_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse type name.
     * 
     * @return Parsed node
     */
    private STNode parseTypeName() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.TYPE_NAME);
            return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse record type descriptor. A record type descriptor body has the following structure.
     * </p>
     * 
     * <code>record-type-descriptor := inclusive-record-type-descriptor | exclusive-record-type-descriptor
     * <br/><br/>inclusive-record-type-descriptor := record { field-descriptor* }
     * <br/><br/>exclusive-record-type-descriptor := record {| field-descriptor* [record-rest-descriptor] |}
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseRecordTypeDescriptor() {
        startContext(ParserRuleContext.RECORD_TYPE_DESCRIPTOR);
        STNode recordKeyword = parseRecordKeyword();
        STNode bodyStartDelimiter = parseRecordBodyStartDelimiter();

        boolean isInclusive = bodyStartDelimiter.kind == SyntaxKind.OPEN_BRACE_TOKEN;
        STNode fields = parseFieldDescriptors(isInclusive);

        STNode bodyEndDelimiter = parseRecordBodyCloseDelimiter();
        endContext();

        return STNodeFactory.createRecordTypeDescriptor(recordKeyword, bodyStartDelimiter, fields, bodyEndDelimiter);
    }

    /**
     * Parse record body start delimiter.
     * 
     * @return Parsed node
     */
    private STNode parseRecordBodyStartDelimiter() {
        STToken token = peek();
        return parseRecordBodyStartDelimiter(token.kind);
    }

    private STNode parseRecordBodyStartDelimiter(SyntaxKind kind) {
        switch (kind) {
            case OPEN_BRACE_PIPE_TOKEN:
                return parseClosedRecordBodyStart();
            case OPEN_BRACE_TOKEN:
                return parseOpenBrace();
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.RECORD_BODY_START);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseRecordBodyStartDelimiter(solution.tokenKind);
        }
    }

    /**
     * Parse closed-record body start delimiter.
     * 
     * @return Parsed node
     */
    private STNode parseClosedRecordBodyStart() {
        STToken token = peek();
        if (token.kind == SyntaxKind.OPEN_BRACE_PIPE_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CLOSED_RECORD_BODY_START);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse record body close delimiter.
     * 
     * @return Parsed node
     */
    private STNode parseRecordBodyCloseDelimiter() {
        STToken token = peek();
        return parseRecordBodyCloseDelimiter(token.kind);
    }

    private STNode parseRecordBodyCloseDelimiter(SyntaxKind kind) {
        switch (kind) {
            case CLOSE_BRACE_PIPE_TOKEN:
                return parseClosedRecordBodyEnd();
            case CLOSE_BRACE_TOKEN:
                return parseCloseBrace();
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.RECORD_BODY_END);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseRecordBodyCloseDelimiter(solution.tokenKind);
        }
    }

    /**
     * Parse closed-record body end delimiter.
     * 
     * @return Parsed node
     */
    private STNode parseClosedRecordBodyEnd() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CLOSE_BRACE_PIPE_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CLOSED_RECORD_BODY_END);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse record keyword.
     * 
     * @return Parsed node
     */
    private STNode parseRecordKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.RECORD_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.RECORD_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse field descriptors.
     * </p>
     * 
     * @return Parsed node
     */
    private STNode parseFieldDescriptors(boolean isInclusive) {
        ArrayList<STNode> recordFields = new ArrayList<>();
        STToken token = peek();
        while (!isEndOfRecordTypeNode(token.kind)) {
            STNode field = parseFieldOrRestDescriptor(isInclusive);
            recordFields.add(field);
            token = peek();

            if (field.kind == SyntaxKind.RECORD_REST_TYPE) {
                break;
            }
        }

        // Following loop will only run if there are more fields after the rest type descriptor.
        // Try to parse them and mark as invalid.
        while (!isEndOfRecordTypeNode(token.kind)) {
            parseFieldOrRestDescriptor(isInclusive);
            // TODO: Mark these nodes as error/invalid nodes.
            this.errorHandler.reportInvalidNode(token, "cannot have more fields after the rest type descriptor");
            token = peek();
        }

        return STNodeFactory.createNodeList(recordFields);
    }

    /**
     * <p>
     * Parse field descriptor or rest descriptor.
     * </p>
     * 
     * <code>
     * <br/><br/>field-descriptor := individual-field-descriptor | record-type-reference
     * <br/><br/><br/>individual-field-descriptor := metadata type-descriptor field-name [? | default-value] ;
     * <br/><br/>field-name := identifier
     * <br/><br/>default-value := = expression
     * <br/><br/>record-type-reference := * type-reference ;
     * <br/><br/>record-rest-descriptor := type-descriptor ... ;
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseFieldOrRestDescriptor(boolean isInclusive) {
        startContext(ParserRuleContext.RECORD_FIELD);

        // record-type-reference
        STToken token = peek();
        if (token.kind == SyntaxKind.ASTERISK_TOKEN) {
            STNode asterisk = consume();
            STNode type = parseTypeReference();
            STNode semicolonToken = parseSemicolon();
            endContext();
            return STNodeFactory.createTypeReference(asterisk, type, semicolonToken);
        }

        // individual-field-descriptor
        STNode type = parseTypeDescriptor();
        STNode fieldOrRestDesc;
        if (isInclusive) {
            STNode fieldName = parseVariableName();
            fieldOrRestDesc = parseFieldDescriptorRhs(type, fieldName);
        } else {
            fieldOrRestDesc = parseFieldOrRestDescriptorRhs(type);
        }

        endContext();
        return fieldOrRestDesc;
    }

    /**
     * Parse type reference.
     * <code>type-reference := identifier | qualified-identifier</code>
     * 
     * @return Type reference node
     */
    private STNode parseTypeReference() {
        return parseQualifiedIdentifier(ParserRuleContext.TYPE_REFERENCE);
    }

    /**
     * Parse identifier or qualified identifier.
     * 
     * @return Identifier node
     */
    private STNode parseQualifiedIdentifier(ParserRuleContext currentCtx) {
        STToken token = peek();
        if (token.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            STNode typeRefOrPkgRef = consume();
            return parseQualifiedIdentifier(typeRefOrPkgRef);
        } else {
            Solution sol = recover(token, currentCtx);
            return sol.recoveredNode;
        }
    }

    /**
     * @param identifier
     * @return
     */
    private STNode parseQualifiedIdentifier(STNode identifier) {
        STToken nextToken = peek(1);
        if (nextToken.kind != SyntaxKind.COLON_TOKEN) {
            return identifier;
        }

        STToken nextNextToken = peek(2);
        if (nextNextToken.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            STToken colon = consume();
            STToken varOrFuncName = consume();
            return STNodeFactory.createQualifiedIdentifier(identifier, colon, varOrFuncName);
        } else {
            this.errorHandler.removeInvalidToken();
            return parseQualifiedIdentifier(identifier);
        }
    }

    /**
     * Parse RHS of a field or rest type descriptor.
     * 
     * @return Parsed node
     */
    private STNode parseFieldOrRestDescriptorRhs(STNode type) {
        STToken token = peek();
        return parseFieldOrRestDescriptorRhs(token.kind, type);
    }

    private STNode parseFieldOrRestDescriptorRhs(SyntaxKind kind, STNode type) {
        switch (kind) {
            case ELLIPSIS_TOKEN:
                STNode ellipsis = parseEllipsis();
                STNode semicolonToken = parseSemicolon();
                return STNodeFactory.createRecordRestDescriptor(type, ellipsis, semicolonToken);
            case IDENTIFIER_TOKEN:
                STNode fieldName = parseVariableName();
                return parseFieldDescriptorRhs(type, fieldName);
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.FIELD_OR_REST_DESCIPTOR_RHS, type);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseFieldOrRestDescriptorRhs(solution.tokenKind, type);
        }
    }

    /**
     * <p>
     * Parse field descriptor rhs.
     * </p>
     * 
     * @return Parsed node
     */
    private STNode parseFieldDescriptorRhs(STNode type, STNode fieldName) {
        STToken token = peek();
        return parseFieldDescriptorRhs(token.kind, type, fieldName);
    }

    /**
     * <p>
     * Parse field descriptor rhs.
     * </p>
     * 
     * <code>
     * field-descriptor := [? | default-value] ;
     * <br/>default-value := = expression
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseFieldDescriptorRhs(SyntaxKind kind, STNode type, STNode fieldName) {
        switch (kind) {
            case SEMICOLON_TOKEN:
                STNode questionMarkToken = STNodeFactory.createEmptyNode();
                STNode semicolonToken = parseSemicolon();
                return STNodeFactory.createRecordField(type, fieldName, questionMarkToken, semicolonToken);
            case QUESTION_MARK_TOKEN:
                questionMarkToken = parseQuestionMark();
                semicolonToken = parseSemicolon();
                return STNodeFactory.createRecordField(type, fieldName, questionMarkToken, semicolonToken);
            case EQUAL_TOKEN:
                // parseRecordDefaultValue();
                STNode equalsToken = parseAssignOp();
                STNode expression = parseExpression();
                semicolonToken = parseSemicolon();
                return STNodeFactory.createRecordFieldWithDefaultValue(type, fieldName, equalsToken, expression,
                        semicolonToken);
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.FIELD_DESCRIPTOR_RHS, type, fieldName);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseFieldDescriptorRhs(solution.tokenKind, type, fieldName);
        }
    }

    /**
     * Parse question mark.
     * 
     * @return Parsed node
     */
    private STNode parseQuestionMark() {
        STToken token = peek();
        if (token.kind == SyntaxKind.QUESTION_MARK_TOKEN) {
            return consume(); // '?' token
        } else {
            Solution sol = recover(token, ParserRuleContext.QUESTION_MARK);
            return sol.recoveredNode;
        }
    }

    /*
     * Statements
     */

    /**
     * Parse statements, until an end of a block is reached.
     * 
     * @return Parsed node
     */
    private STNode parseStatements() {
        STToken token = peek();

        ArrayList<STNode> stmts = new ArrayList<>();
        while (!isEndOfBlockNode(token.kind)) {
            stmts.add(parseStatement());
            token = peek();
        }

        return STNodeFactory.createNodeList(stmts);
    }

    /**
     * Parse a single statement.
     * 
     * @return Parsed node
     */
    protected STNode parseStatement() {
        STToken token = peek();
        return parseStatement(token.kind);
    }

    /**
     * Parse a single statement, given the next token kind.
     * 
     * @param tokenKind Next tokenKind
     * @return Parsed node
     */
    private STNode parseStatement(SyntaxKind tokenKind) {
        switch (tokenKind) {
            // TODO: add all 'type starting tokens' here. should be same as 'parseTypeDescriptor(...)'
            case SIMPLE_TYPE:
            case RECORD_KEYWORD:
            case OBJECT_KEYWORD:
            case ABSTRACT_KEYWORD:
            case CLIENT_KEYWORD:
                // If the statement starts with a type, then its a var declaration.
                // This is an optimization since if we know the next token is a type, then
                // we can parse the var-def faster.
                return parseVariableDeclStmt();
            case IDENTIFIER_TOKEN:
                // If the statement starts with an identifier, it could be either an assignment
                // statement or a var-decl-stmt with a user defined type.
                return parseAssignmentOrVarDecl();
            case IF_KEYWORD:
                return parseIfElseBlock();
            case WHILE_KEYWORD:
                return parseWhileStatement();
            case CHECK_KEYWORD:
            case CHECKPANIC_KEYWORD:
                return parseCallStatementWithCheck();
            default:
                // If the next token in the token stream does not match to any of the statements and
                // if it is not the end of statement, then try to fix it and continue.
                if (isEndOfBlockNode(tokenKind)) {
                    // TODO: revisit this
                    return STNodeFactory.createEmptyNode();
                }

                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.STATEMENT);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseStatement(solution.tokenKind);
        }
    }

    /**
     * <p>
     * Parse local variable declaration statement. A local variable declaration can take following format.
     * </p>
     * 
     * <code>
     * local-var-decl-stmt := local-init-var-decl-stmt | local-no-init-var-decl-stmt
     * <br/><br/>
     * local-init-var-decl-stmt := [annots] [final] typed-binding-pattern = action-or-expr ;
     * <br/><br/>
     * local-no-init-var-decl-stmt := [annots] [final] type-descriptor variable-name ;
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseVariableDeclStmt() {
        startContext(ParserRuleContext.VAR_DECL_STMT);
        STNode type = parseTypeDescriptor();
        STNode varName = parseVariableName();
        STNode varDecl = parseVarDeclRhs(type, varName);
        endContext();
        return varDecl;
    }

    /**
     * <p>
     * Parse the right hand side of a variable declaration statement.
     * </p>
     * <code>
     * var-decl-rhs := ; | = action-or-expr ;
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseVarDeclRhs(STNode type, STNode varName) {
        STToken token = peek();
        return parseVarDeclRhs(token.kind, type, varName);
    }

    /**
     * Parse the right hand side of a variable declaration statement, given the
     * next token kind.
     * 
     * @param tokenKind Next token kind
     * @return Parsed node
     */
    private STNode parseVarDeclRhs(SyntaxKind tokenKind, STNode type, STNode varName) {
        STNode assign;
        STNode expr;
        STNode semicolon;

        switch (tokenKind) {
            case EQUAL_TOKEN:
                assign = parseAssignOp();
                expr = parseExpression();
                semicolon = parseSemicolon();
                break;
            case SEMICOLON_TOKEN:
                assign = STNodeFactory.createEmptyNode();
                expr = STNodeFactory.createEmptyNode();
                semicolon = parseSemicolon();
                break;
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.VAR_DECL_STMT_RHS, type, varName);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseVarDeclRhs(solution.tokenKind, type, varName);
        }

        return STNodeFactory.createVariableDeclaration(SyntaxKind.LOCAL_VARIABLE_DECL, type, varName, assign, expr,
                semicolon);
    }

    /**
     * If the statement starts with an identifier, it could be either an assignment statement or
     * a var-decl-stmt with a user defined type. This method will parse such ambiguous scenarios.
     * 
     * @return Parsed node
     */
    private STNode parseAssignmentOrVarDecl() {
        startContext(ParserRuleContext.ASSIGNMENT_OR_VAR_DECL_STMT);
        STNode identifier = parseStatementStartIdentifier();
        STNode assignmentOrVarDecl = parseAssignmentOrVarDeclRhs(identifier);
        endContext();
        return assignmentOrVarDecl;
    }

    /**
     * Parse the second portion of an assignment statement or a var-decl statement when ambiguous.
     * 
     * @param typeOrVarName Type name or variable name
     * @return Parsed node
     */
    private STNode parseAssignmentOrVarDeclRhs(STNode typeOrVarName) {
        STToken token = peek();
        return parseAssignmentOrVarDeclRhs(token.kind, typeOrVarName);
    }

    /**
     * Parse the second portion of an assignment statement or a var-decl statement when ambiguous,
     * given the next token kind.
     * 
     * @param nextTokenKind Next token kind
     * @param identifier Identifier at the start of the statement
     * @return Parsed node
     */
    private STNode parseAssignmentOrVarDeclRhs(SyntaxKind nextTokenKind, STNode identifier) {
        switch (nextTokenKind) {
            case IDENTIFIER_TOKEN:
                STNode varName = parseVariableName();
                return parseVarDeclRhs(identifier, varName);
            case EQUAL_TOKEN:
                return parseAssignmentStmtRhs(identifier);
            case OPEN_PAREN_TOKEN:
            case DOT_TOKEN:
            case OPEN_BRACKET_TOKEN:
                STNode expr = parseExpressionRhs(identifier, true);
                switch (expr.kind) {
                    case METHOD_CALL:
                    case FUNCTION_CALL:
                        return parseCallStatement(expr);
                    case FIELD_ACCESS:
                    case MEMBER_ACCESS:
                        return parseAssignmentStmtRhs(expr);
                    default:
                        // TODO: Add proper error reporting
                        this.errorHandler.reportInvalidNode(null,
                                "left hand side of an assignment must be a variable reference");
                        return parseCallStatement(expr);
                }
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.ASSIGNMENT_OR_VAR_DECL_STMT_RHS, identifier);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseAssignmentOrVarDeclRhs(solution.tokenKind, identifier);
        }
    }

    /**
     * <p>
     * Parse assignment statement, which takes the following format.
     * </p>
     * <code>assignment-stmt := lvexpr = action-or-expr ;</code>
     * 
     * @return Parsed node
     */
    private STNode parseAssignmentStmt() {
        startContext(ParserRuleContext.ASSIGNMENT_STMT);
        STNode varName = parseVariableName();
        STNode assignmentStmt = parseAssignmentStmtRhs(varName);
        endContext();
        return assignmentStmt;
    }

    /**
     * <p>
     * Parse the RHS portion of the assignment.
     * </p>
     * <code>assignment-stmt-rhs := = action-or-expr ;</code>
     * 
     * @param expression LHS expression
     * @return Parsed node
     */
    private STNode parseAssignmentStmtRhs(STNode expression) {
        STNode assign = parseAssignOp();
        STNode expr = parseExpression();
        STNode semicolon = parseSemicolon();
        return STNodeFactory.createAssignmentStatement(SyntaxKind.ASSIGNMENT_STATEMENT, expression, assign, expr,
                semicolon);
    }

    /*
     * Expressions
     */

    /**
     * Parse expression. This will start parsing expressions from the lowest level of precedence.
     * 
     * @return Parsed node
     */
    private STNode parseExpression() {
        return parseExpression(OperatorPrecedence.BINARY_COMPARE, false);
    }

    /**
     * Parse an expression that has an equal or higher precedence than a given level.
     * 
     * @param precedenceLevel Precedence level of expression to be parsed
     * @return Parsed node
     */
    private STNode parseExpression(OperatorPrecedence precedenceLevel, boolean isAssignmentLhs) {
        STNode expr = parseTerminalExpression();
        return parseExpressionRhs(precedenceLevel, expr, isAssignmentLhs);
    }

    /**
     * Parse terminal expressions. A terminal expression has the highest precedence level
     * out of all expressions, and will be at the leaves of an expression tree.
     * 
     * @return Parsed node
     */
    private STNode parseTerminalExpression() {
        STToken token = peek();
        return parseTerminalExpression(token.kind);
    }

    private STNode parseTerminalExpression(SyntaxKind kind) {
        // TODO: Whenever a new expression start is added, make sure to
        // add it to all the other places as well.
        switch (kind) {
            case DECIMAL_INTEGER_LITERAL:
            case HEX_INTEGER_LITERAL:
                return parseLiteral();
            case IDENTIFIER_TOKEN:
                return parseQualifiedIdentifier(ParserRuleContext.VARIABLE_NAME);
            case OPEN_PAREN_TOKEN:
                return parseBracedExpression();
            case TRUE_KEYWORD:
            case FALSE_KEYWORD:
                return parseBooleanLiteral();
            case CHECK_KEYWORD:
            case CHECKPANIC_KEYWORD:
                return parseCheckExpression();
            default:
                Solution solution = recover(peek(), ParserRuleContext.EXPRESSION);
                return solution.recoveredNode;
        }
    }

    private STNode parseExpressionRhs(STNode lhsExpr) {
        return parseExpressionRhs(OperatorPrecedence.BINARY_COMPARE, lhsExpr, false);
    }

    /**
     * Parse the right-hand-side of an expression.
     * 
     * @return Parsed node
     */
    private STNode parseExpressionRhs(STNode lhsExpr, boolean isAssignmentLhs) {
        return parseExpressionRhs(OperatorPrecedence.BINARY_COMPARE, lhsExpr, isAssignmentLhs);
    }

    /**
     * <p>
     * Parse the right-hand-side of an expression.
     * </p>
     * <code>expr-rhs := (binary-op expression 
     *                              | dot identifier 
     *                              | open-bracket expression close-bracket
     *                          )*</code>
     * 
     * @param precedenceLevel Precedence level of the expression that is being parsed currently
     * @param lhsExpr LHS expression of the expression
     * @return Parsed node
     */
    private STNode parseExpressionRhs(OperatorPrecedence precedenceLevel, STNode lhsExpr, boolean isAssignmentLhs) {
        STToken token = peek();
        return parseExpressionRhs(precedenceLevel, token.kind, lhsExpr, isAssignmentLhs);
    }

    /**
     * Parse the right hand side of an expression given the next token kind.
     * 
     * @param currentPrecedenceLevel Precedence level of the expression that is being parsed currently
     * @param tokenKind Next token kind
     * @return Parsed node
     */
    private STNode parseExpressionRhs(OperatorPrecedence currentPrecedenceLevel, SyntaxKind tokenKind, STNode lhsExpr,
                                      boolean isAssignmentLhs) {
        if (isEndOfExpression(tokenKind, isAssignmentLhs)) {
            return lhsExpr;
        }

        if (!isValidExprRhsStart(tokenKind)) {
            STToken token = peek();
            Solution solution = recover(token, ParserRuleContext.EXPRESSION_RHS, lhsExpr);

            // If the current rule was recovered by removing a token,
            // then this entire rule is already parsed while recovering.
            // so we done need to parse the remaining of this rule again.
            // Proceed only if the recovery action was an insertion.
            if (solution.action == Action.REMOVE) {
                return solution.recoveredNode;
            }

            // If the parser recovered by inserting a token, then try to re-parse the same
            // rule with the inserted token. This is done to pick the correct branch to
            // continue the parsing.
            if (solution.ctx == ParserRuleContext.BINARY_OPERATOR) {
                // We come here if the operator is missing. Treat this as injecting an operator
                // that matches to the current operator precedence level, and continue.
                SyntaxKind binaryOpKind = getOperatorKindToInsert(currentPrecedenceLevel);
                return parseExpressionRhs(currentPrecedenceLevel, binaryOpKind, lhsExpr, isAssignmentLhs);
            } else {
                return parseExpressionRhs(currentPrecedenceLevel, solution.tokenKind, lhsExpr, isAssignmentLhs);
            }
        }

        // If the precedence level of the operator that was being parsed is higher than
        // the newly found (next) operator, then return and finish the previous expr,
        // because it has a higher precedence.
        OperatorPrecedence nextOperatorPrecedence = getOpPrecedence(tokenKind);
        if (currentPrecedenceLevel.isHigherThan(nextOperatorPrecedence)) {
            return lhsExpr;
        }

        STNode newLhsExpr;
        switch (tokenKind) {
            case OPEN_PAREN_TOKEN:
                newLhsExpr = parseFuncCall(lhsExpr);
                break;
            case OPEN_BRACKET_TOKEN:
                newLhsExpr = parseMemberAccessExpr(lhsExpr);
                break;
            case DOT_TOKEN:
                if (peek(2).kind == SyntaxKind.IDENTIFIER_TOKEN) {
                    newLhsExpr = parseFieldAccessOrMethodCall(lhsExpr);
                    break;
                }
                // else fall through
            default:
                STNode operator = parseBinaryOperator();

                // Parse the expression that follows the binary operator, until a operator
                // with different precedence is encountered. If an operator with a lower
                // precedence is reached, then come back here and finish the current
                // binary expr. If a an operator with higher precedence level is reached,
                // then complete that binary-expr, come back here and finish the current expr.
                STNode rhsExpr = parseExpression(nextOperatorPrecedence, isAssignmentLhs);
                newLhsExpr =
                        STNodeFactory.createBinaryExpression(SyntaxKind.BINARY_EXPRESSION, lhsExpr, operator, rhsExpr);
                break;
        }

        // Then continue the operators with the same precedence level.
        return parseExpressionRhs(currentPrecedenceLevel, newLhsExpr, isAssignmentLhs);
    }

    private boolean isValidExprRhsStart(SyntaxKind tokenKind) {
        switch (tokenKind) {
            case OPEN_PAREN_TOKEN:
            case DOT_TOKEN:
            case OPEN_BRACKET_TOKEN:
                return true;
            default:
                return isBinaryOperator(tokenKind);
        }
    }

    /**
     * Parse member access expression.
     * 
     * @param lhsExpr Container expression
     * @return Member access expression
     */
    private STNode parseMemberAccessExpr(STNode lhsExpr) {
        // Next token is already validated before coming here. Hence just consume.
        STNode openBracket = consume();

        STNode keyExpr;
        if (peek().kind == SyntaxKind.CLOSE_BRACKET_TOKEN) {
            this.errorHandler.reportMissingTokenError("missing expression");
            keyExpr = STNodeFactory.createMissingToken(SyntaxKind.IDENTIFIER_TOKEN);
        } else {
            keyExpr = parseExpression();
        }
        STNode closeBracket = parseCloseBracket();
        return STNodeFactory.createMemberAccessExpression(lhsExpr, openBracket, keyExpr, closeBracket);
    }

    /**
     * Parse close bracket.
     * 
     * @return Parsed node
     */
    private STNode parseCloseBracket() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CLOSE_BRACKET_TOKEN) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CLOSE_BRACKET);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse field access expression and method call expression.
     * 
     * @param lhsExpr Preceding expression of the field access or method call
     * @return One of <code>field-access-expression</code> or <code>method-call-expression</code>.
     */
    private STNode parseFieldAccessOrMethodCall(STNode lhsExpr) {
        // Next two tokens are already validated before coming here.
        // Hence just consume.
        STNode dotToken = consume();
        STNode fieldOrMethodName = consume();

        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.OPEN_PAREN_TOKEN) {
            // function invocation
            STNode openParen = parseOpenParenthesis();
            STNode args = parseArgsList();
            STNode closeParen = parseCloseParenthesis();
            return STNodeFactory.createMethodCallExpression(lhsExpr, dotToken, fieldOrMethodName, openParen, args,
                    closeParen);
        }

        // Everything else is field-access
        return STNodeFactory.createFieldAccessExpression(lhsExpr, dotToken, fieldOrMethodName);
    }

    /**
     * <p>
     * Parse braced expression.
     * </p>
     * <code>braced-expr := ( expression )</code>
     * 
     * @return Parsed node
     */
    private STNode parseBracedExpression() {
        STNode openParen = parseOpenParenthesis();
        STNode expr = parseExpression();
        STNode closeParen = parseCloseParenthesis();
        return STNodeFactory.createBracedExpression(SyntaxKind.BRACED_EXPRESSION, openParen, expr, closeParen);
    }

    /**
     * Check whether the given token is an end of a expression.
     * 
     * @param tokenKind Token to check
     * @return <code>true</code> if the token represents an end of a block. <code>false</code> otherwise
     */
    private boolean isEndOfExpression(SyntaxKind tokenKind, boolean isAssignmentLhs) {
        switch (tokenKind) {
            case CLOSE_BRACE_TOKEN:
            case OPEN_BRACE_TOKEN:
            case CLOSE_PAREN_TOKEN:
            case CLOSE_BRACKET_TOKEN:
            case SEMICOLON_TOKEN:
            case COMMA_TOKEN:
            case PUBLIC_KEYWORD:
            case FUNCTION_KEYWORD:
            case EOF_TOKEN:
                return true;
            default:
                if (isAssignmentLhs) {
                    return isBinaryOperator(tokenKind);
                }
                return false;
        }
    }

    /**
     * Parse expressions that references variable or functions at the start of the expression.
     * 
     * @return Parsed node
     */
    private STNode parseLiteral() {
        return consume();
    }

    /**
     * Parse function call expression.
     * <code>function-call-expr := function-reference ( arg-list )
     * function-reference := variable-reference</code>
     * 
     * @param identifier Function name
     * @return Function call expression
     */
    private STNode parseFuncCall(STNode identifier) {
        STNode openParen = parseOpenParenthesis();
        STNode args = parseArgsList();
        STNode closeParen = parseCloseParenthesis();
        return STNodeFactory.createFunctionCallExpression(identifier, openParen, args, closeParen);
    }

    /**
     * Parse function call argument list.
     * 
     * @return Parsed agrs list
     */
    private STNode parseArgsList() {
        startContext(ParserRuleContext.ARG_LIST);
        ArrayList<STNode> argsList = new ArrayList<>();

        STToken token = peek();
        if (isEndOfParametersList(token.kind)) {
            STNode args = STNodeFactory.createNodeList(argsList);
            endContext();
            return args;
        }

        SyntaxKind lastProcessedArgKind = parseFirstArg(argsList);
        parseFollowUpArg(argsList, lastProcessedArgKind);

        STNode args = STNodeFactory.createNodeList(argsList);
        endContext();
        return args;
    }

    /**
     * Parse the first argument of a function call.
     * 
     * @param argsList Arguments list to which the parsed argument must be added
     * @return Kind of the argument first argument.
     */
    private SyntaxKind parseFirstArg(ArrayList<STNode> argsList) {
        startContext(ParserRuleContext.ARG);

        // Comma precedes the first argument is an empty node, since it doesn't exist.
        STNode leadingComma = STNodeFactory.createEmptyNode();
        STNode arg = parseArg(leadingComma);
        endContext();

        if (SyntaxKind.POSITIONAL_ARG.ordinal() <= arg.kind.ordinal()) {
            argsList.add(arg);
            return arg.kind;
        } else {
            reportInvalidOrderOfArgs(peek(), SyntaxKind.POSITIONAL_ARG, arg.kind);
            return SyntaxKind.POSITIONAL_ARG;
        }
    }

    /**
     * Parse follow up arguments.
     * 
     * @param argsList Arguments list to which the parsed argument must be added
     * @param lastProcessedArgKind Kind of the argument processed prior to this
     */
    private void parseFollowUpArg(ArrayList<STNode> argsList, SyntaxKind lastProcessedArgKind) {
        STToken nextToken = peek();
        while (!isEndOfParametersList(nextToken.kind)) {
            startContext(ParserRuleContext.ARG);

            STNode leadingComma = parseComma();

            // If there's an extra comma at the end of arguments list, remove it.
            // Then stop the argument parsing.
            nextToken = peek();
            if (isEndOfParametersList(nextToken.kind)) {
                this.errorHandler.reportInvalidNode((STToken) leadingComma, "invalid token " + leadingComma);
                endContext();
                break;
            }

            STNode arg = parseArg(nextToken.kind, leadingComma);
            if (lastProcessedArgKind.ordinal() <= arg.kind.ordinal()) {
                if (lastProcessedArgKind == SyntaxKind.REST_ARG && arg.kind == SyntaxKind.REST_ARG) {
                    this.errorHandler.reportInvalidNode(nextToken, "cannot more than one rest arg");
                } else {
                    argsList.add(arg);
                    lastProcessedArgKind = arg.kind;
                }
            } else {
                reportInvalidOrderOfArgs(nextToken, lastProcessedArgKind, arg.kind);
            }

            nextToken = peek();
            endContext();
        }
    }

    /**
     * Report invalid order of args.
     * 
     * @param token Staring token of the arg.
     * @param lastArgKind Kind of the previously processed arg
     * @param argKind Current arg
     */
    private void reportInvalidOrderOfArgs(STToken token, SyntaxKind lastArgKind, SyntaxKind argKind) {
        this.errorHandler.reportInvalidNode(token, "cannot have a " + argKind + " after the " + lastArgKind);
    }

    /**
     * Parse function call argument.
     * 
     * @param leadingComma Comma that occurs before the param
     * @return Parsed argument node
     */
    private STNode parseArg(STNode leadingComma) {
        STToken token = peek();
        return parseArg(token.kind, leadingComma);
    }

    private STNode parseArg(SyntaxKind kind, STNode leadingComma) {
        STNode arg;
        switch (kind) {
            case ELLIPSIS_TOKEN:
                STToken ellipsis = consume();
                STNode expr = parseExpression();
                arg = STNodeFactory.createRestArg(leadingComma, ellipsis, expr);
                break;

            // Identifier can means two things: either its a named-arg, or just an expression.
            case IDENTIFIER_TOKEN:
                // TODO: Handle package-qualified var-refs (i.e: qualified-identifier).
                arg = parseNamedOrPositionalArg(leadingComma);
                break;

            // Any other expression goes here
            case DECIMAL_INTEGER_LITERAL:
            case HEX_INTEGER_LITERAL:
            case OPEN_PAREN_TOKEN:
            case TRUE_KEYWORD:
            case FALSE_KEYWORD:
            default:
                expr = parseExpression();
                arg = STNodeFactory.createPositionalArg(leadingComma, expr);
                break;
        }

        return arg;
    }

    /**
     * Parse positional or named arg. This method assumed peek()/peek(1)
     * is always an identifier.
     * 
     * @param leadingComma Comma that occurs before the param
     * @return Parsed argument node
     */
    private STNode parseNamedOrPositionalArg(STNode leadingComma) {
        STToken secondToken = peek(2);
        switch (secondToken.kind) {
            case EQUAL_TOKEN:
                STNode argNameOrVarRef = consume();
                STNode equal = parseAssignOp();
                STNode expr = parseExpression();
                return STNodeFactory.createNamedArg(leadingComma, argNameOrVarRef, equal, expr);
            case COMMA_TOKEN:
            case CLOSE_PAREN_TOKEN:
                argNameOrVarRef = consume();
                return STNodeFactory.createPositionalArg(leadingComma, argNameOrVarRef);

            // Treat everything else as a single expression. If something is missing,
            // expression-parsing will recover it.
            case DECIMAL_INTEGER_LITERAL:
            case HEX_INTEGER_LITERAL:
            case IDENTIFIER_TOKEN:
            case OPEN_PAREN_TOKEN:
            case TRUE_KEYWORD:
            case FALSE_KEYWORD:
            default:
                expr = parseExpression();
                return STNodeFactory.createPositionalArg(leadingComma, expr);
        }
    }

    /**
     * Parse object type descriptor.
     * 
     * @return Parsed node
     */
    private STNode parseObjectTypeDescriptor() {
        startContext(ParserRuleContext.OBJECT_TYPE_DESCRIPTOR);
        STNode objectTypeQualifiers = parseObjectTypeQualifiers();
        STNode objectKeyword = parseObjectKeyword();
        STNode openBrace = parseOpenBrace();
        STNode objectMembers = parseObjectMembers();
        STNode closeBrace = parseCloseBrace();
        endContext();

        return STNodeFactory.createObjectTypeDescriptor(objectTypeQualifiers, objectKeyword, openBrace, objectMembers,
                closeBrace);
    }

    /**
     * Parse object type qualifiers.
     * 
     * @return Parsed node
     */
    private STNode parseObjectTypeQualifiers() {
        STToken nextToken = peek();
        return parseObjectTypeQualifiers(nextToken.kind);
    }

    private STNode parseObjectTypeQualifiers(SyntaxKind kind) {
        List<STNode> qualifiers = new ArrayList<>();
        STNode firstQualifier;
        switch (kind) {
            case CLIENT_KEYWORD:
                STNode clientKeyword = parseClientKeyword();
                firstQualifier = clientKeyword;
                break;
            case ABSTRACT_KEYWORD:
                STNode abstractKeyword = parseAbstractKeyword();
                firstQualifier = abstractKeyword;
                break;
            case OBJECT_KEYWORD:
                return STNodeFactory.createNodeList(qualifiers);
            default:
                Solution solution = recover(peek(), ParserRuleContext.OBJECT_TYPE_FIRST_QUALIFIER);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseObjectTypeQualifiers(solution.tokenKind);
        }

        // Parse the second qualifier if available.
        STNode secondQualifier = parseObjectTypeSecondQualifier(firstQualifier);

        qualifiers.add(firstQualifier);
        if (secondQualifier != null) {
            qualifiers.add(secondQualifier);
        }
        return STNodeFactory.createNodeList(qualifiers);
    }

    private STNode parseObjectTypeSecondQualifier(STNode firstQualifier) {
        STToken nextToken = peek();
        return parseObjectTypeSecondQualifier(nextToken.kind, firstQualifier);
    }

    private STNode parseObjectTypeSecondQualifier(SyntaxKind kind, STNode firstQualifier) {
        if (firstQualifier.kind != kind) {
            switch (kind) {
                case CLIENT_KEYWORD:
                    return parseClientKeyword();
                case ABSTRACT_KEYWORD:
                    return parseAbstractKeyword();
                case OBJECT_KEYWORD:
                    return null;
                default:
                    break;
            }
        }

        Solution solution = recover(peek(), ParserRuleContext.OBJECT_TYPE_SECOND_QUALIFIER, firstQualifier);

        // If the parser recovered by inserting a token, then try to re-parse the same
        // rule with the inserted token. This is done to pick the correct branch
        // to continue the parsing.
        if (solution.action == Action.REMOVE) {
            return solution.recoveredNode;
        }

        return parseObjectTypeSecondQualifier(solution.tokenKind, firstQualifier);
    }

    /**
     * Parse client keyword.
     * 
     * @return Parsed node
     */
    private STNode parseClientKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CLIENT_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CLIENT_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse abstract keyword.
     * 
     * @return Parsed node
     */
    private STNode parseAbstractKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.ABSTRACT_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.ABSTRACT_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse object keyword.
     * 
     * @return Parsed node
     */
    private STNode parseObjectKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.OBJECT_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.OBJECT_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse object members.
     * 
     * @return Parsed node
     */
    private STNode parseObjectMembers() {
        ArrayList<STNode> objectMembers = new ArrayList<>();
        STToken nextToken = peek(1);
        STToken nextNextToken = peek(2);
        while (!isEndOfObjectTypeNode(nextToken.kind, nextNextToken.kind)) {
            startContext(ParserRuleContext.OBJECT_MEMBER);
            STNode field = parseObjectMember(nextToken.kind);
            endContext();

            if (field == null) {
                break;
            }
            objectMembers.add(field);
            nextToken = peek();
        }

        return STNodeFactory.createNodeList(objectMembers);
    }

    private STNode parseObjectMember() {
        STToken nextToken = peek();
        return parseObjectMember(nextToken.kind);
    }

    /**
     * 
     */
    private STNode parseObjectMember(SyntaxKind kind) {
        STNode member;
        switch (kind) {
            case ASTERISK_TOKEN:
                STNode asterisk = consume();
                STNode type = parseTypeReference();
                STNode semicolonToken = parseSemicolon();
                member = STNodeFactory.createTypeReference(asterisk, type, semicolonToken);
                break;
            case PUBLIC_KEYWORD:
            case PRIVATE_KEYWORD:
                STNode visibilityQualifier = parseObjectMemberVisibility();
                member = parseObjectMethodOrField(visibilityQualifier);
                break;
            case REMOTE_KEYWORD:
                member = parseObjectMethodOrField(STNodeFactory.createEmptyNode());
                break;
            case FUNCTION_KEYWORD:
                member = parseObjectMethod(STNodeFactory.createEmptyNode());
                break;

            // All 'type starting tokens' here. should be same as 'parseTypeDescriptor(...)'
            case IDENTIFIER_TOKEN:
            case SIMPLE_TYPE:
            case RECORD_KEYWORD:
            case OBJECT_KEYWORD:
            case ABSTRACT_KEYWORD:
            case CLIENT_KEYWORD:
                member = parseObjectField(STNodeFactory.createEmptyNode());
                break;
            default:
                Solution solution = recover(peek(), ParserRuleContext.OBJECT_MEMBER);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                if (isEndOfObjectTypeNode(solution.tokenKind, kind)) {
                    return null;
                }
                return parseObjectMember(solution.tokenKind);
        }

        return member;
    }

    private STNode parseObjectMethodOrField(STNode methodQualifiers) {
        STToken nextToken = peek(1);
        STToken nextNextToken = peek(2);
        return parseObjectMethodOrField(nextToken.kind, nextNextToken.kind, methodQualifiers);
    }

    /**
     * Parse an object member, given the visibility modifier. Object member can have
     * only one visibility qualifier. This mean the methodQualifiers list can have
     * one qualifier at-most.
     * 
     * @param visibilityQualifiers Visibility qualifiers. A modifier can be
     *            a syntax node with either 'PUBLIC' or 'PRIVATE'.
     * @param nextTokenKind Next token kind
     * @param nextNextTokenKind Kind of the token after the
     * @return Parse object member node
     */
    private STNode parseObjectMethodOrField(SyntaxKind nextTokenKind, SyntaxKind nextNextTokenKind,
                                            STNode visibilityQualifiers) {
        switch (nextTokenKind) {
            case REMOTE_KEYWORD:
                STNode remoteKeyword = parseRemoteKeyword();
                ArrayList<STNode> methodQualifiers = new ArrayList<>();
                if (visibilityQualifiers.kind != SyntaxKind.NONE) {
                    methodQualifiers.add(visibilityQualifiers);
                }
                methodQualifiers.add(remoteKeyword);
                return parseObjectMethod(STNodeFactory.createNodeList(methodQualifiers));
            case FUNCTION_KEYWORD:
                return parseObjectMethod(visibilityQualifiers);

            // All 'type starting tokens' here. should be same as 'parseTypeDescriptor(...)'
            case IDENTIFIER_TOKEN:
            case SIMPLE_TYPE:
            case RECORD_KEYWORD:
            case OBJECT_KEYWORD:
            case ABSTRACT_KEYWORD:
            case CLIENT_KEYWORD:
                // Here we try to catch the common user error of missing the function keyword.
                // In such cases, lookahead for the open-parenthesis and figure out whether
                // this is an object-method with missing name. If yes, then try to recover.
                if (nextNextTokenKind != SyntaxKind.OPEN_PAREN_TOKEN) {
                    return parseObjectField(visibilityQualifiers);
                }

                // Else, fall through
            default:
                Solution solution = recover(peek(), ParserRuleContext.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY,
                        visibilityQualifiers);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseObjectMethodOrField(solution.tokenKind, nextTokenKind, visibilityQualifiers);
        }
    }

    private STNode parseObjectMemberVisibility() {
        STToken token = peek();
        if (token.kind == SyntaxKind.PUBLIC_KEYWORD || token.kind == SyntaxKind.PRIVATE_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.PUBLIC_KEYWORD);
            return sol.recoveredNode;
        }
    }

    private STNode parseRemoteKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.REMOTE_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.REMOTE_KEYWORD);
            return sol.recoveredNode;
        }
    }

    private STNode parseObjectField(STNode methodQualifiers) {
        STNode type = parseTypeDescriptor();
        STNode fieldName = parseVariableName();
        return parseObjectFieldRhs(methodQualifiers, type, fieldName);
    }

    /**
     * Parse object field rhs, and complete the object field parsing. Returns the parsed object field.
     * 
     * @param visibilityQualifier
     * @param type
     * @param fieldName
     * @return Parsed object field
     */
    private STNode parseObjectFieldRhs(STNode visibilityQualifier, STNode type, STNode fieldName) {
        STToken nextToken = peek();
        return parseObjectFieldRhs(nextToken.kind, visibilityQualifier, type, fieldName);
    }

    /**
     * Parse object field rhs, and complete the object field parsing. Returns the parsed object field.
     * 
     * @param nextTokenKind
     * @param visibilityQualifier
     * @param type
     * @param fieldName
     * @return Parsed object field
     */
    private STNode parseObjectFieldRhs(SyntaxKind nextTokenKind, STNode visibilityQualifier, STNode type,
                                       STNode fieldName) {
        STNode equalsToken;
        STNode expression;
        STNode semicolonToken;
        switch (nextTokenKind) {
            case SEMICOLON_TOKEN:
                equalsToken = STNodeFactory.createEmptyNode();
                expression = STNodeFactory.createEmptyNode();
                semicolonToken = parseSemicolon();
                break;
            case EQUAL_TOKEN:
                equalsToken = parseAssignOp();
                expression = parseExpression();
                semicolonToken = parseSemicolon();
                break;
            default:
                STToken token = peek();
                Solution solution =
                        recover(token, ParserRuleContext.OBJECT_FIELD_RHS, visibilityQualifier, type, fieldName);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseObjectFieldRhs(solution.tokenKind, visibilityQualifier, type, fieldName);
        }

        return STNodeFactory.createObjectField(visibilityQualifier, type, fieldName, equalsToken, expression,
                semicolonToken);
    }

    private STNode parseObjectMethod(STNode methodQualifiers) {
        return parseFunctionDefinition(methodQualifiers);
    }

    /**
     * Parse if-else statement.
     * <code>
     * if-else-stmt := if expression block-stmt [else-block]
     * </code>
     * 
     * @return If-else block
     */
    private STNode parseIfElseBlock() {
        startContext(ParserRuleContext.IF_BLOCK);
        STNode ifKeyword = parseIfKeyword();
        STNode condition = parseExpression();
        STNode ifBody = parseBlockNode();
        endContext();

        STNode elseBody = parseElseBlock();
        return STNodeFactory.createIfElseStatement(ifKeyword, condition, ifBody, elseBody);
    }

    /**
     * Parse if-keyword.
     * 
     * @return Parsed if-keyword node
     */
    private STNode parseIfKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IF_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.IF_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse else-keyword.
     * 
     * @return Parsed else keyword node
     */
    private STNode parseElseKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.ELSE_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.ELSE_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse block node.
     * <code>
     * block-stmt := { sequence-stmt }
     * sequence-stmt := statement*
     * </code>
     * 
     * @return Parse block node
     */
    private STNode parseBlockNode() {
        startContext(ParserRuleContext.BLOCK_STMT);
        STNode openBrace = parseOpenBrace();
        STNode stmts = parseStatements();
        STNode closeBrace = parseCloseBrace();
        endContext();
        return STNodeFactory.createBlockStatement(openBrace, stmts, closeBrace);
    }

    /**
     * Parse else block.
     * <code>else-block := else (if-else-stmt | block-stmt)</code>
     * 
     * @return Else block
     */
    private STNode parseElseBlock() {
        STToken nextToken = peek();
        if (nextToken.kind != SyntaxKind.ELSE_KEYWORD) {
            return STNodeFactory.createEmptyNode();
        }

        STNode elseKeyword = parseElseKeyword();
        STNode elseBody = parseElseBody();
        return STNodeFactory.createElseBlock(elseKeyword, elseBody);
    }

    /**
     * Parse else node body.
     * <code>else-body := if-else-stmt | block-stmt</code>
     * 
     * @return Else node body
     */
    private STNode parseElseBody() {
        STToken nextToken = peek();
        return parseElseBody(nextToken.kind);
    }

    private STNode parseElseBody(SyntaxKind nextTokenKind) {
        switch (nextTokenKind) {
            case IF_KEYWORD:
                return parseIfElseBlock();
            case OPEN_BRACE_TOKEN:
                return parseBlockNode();
            default:
                STToken token = peek();
                Solution solution = recover(token, ParserRuleContext.ELSE_BODY);

                // If the parser recovered by inserting a token, then try to re-parse the same
                // rule with the inserted token. This is done to pick the correct branch
                // to continue the parsing.
                if (solution.action == Action.REMOVE) {
                    return solution.recoveredNode;
                }

                return parseElseBody(solution.tokenKind);
        }
    }

    /**
     * Parse while statement.
     * <code>while-stmt := while expression block-stmt</code>
     * 
     * @return While statement
     */
    private STNode parseWhileStatement() {
        startContext(ParserRuleContext.WHILE_BLOCK);
        STNode whileKeyword = parseWhileKeyword();
        STNode condition = parseExpression();
        STNode whileBody = parseBlockNode();
        endContext();
        return STNodeFactory.createWhileStatement(whileKeyword, condition, whileBody);
    }

    /**
     * Parse while-keyword.
     * 
     * @return While-keyword node
     */
    private STNode parseWhileKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.WHILE_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.WHILE_KEYWORD);
            return sol.recoveredNode;
        }
    }

    /**
     * Parse boolean literal.
     * 
     * @return Parsed node
     */
    private STNode parseBooleanLiteral() {
        STToken token = peek();
        switch (token.kind) {
            case TRUE_KEYWORD:
            case FALSE_KEYWORD:
                return consume();
            default:
                Solution sol = recover(token, ParserRuleContext.BOOLEAN_LITERAL);
                return sol.recoveredNode;
        }
    }

    /**
     * <p>
     * Parse call statement, given the call expression.
     * <p>
     * <code>
     * call-stmt := call-expr ;
     * <br/>
     * call-expr := function-call-expr | method-call-expr | checking-keyword call-expr
     * </code>
     * 
     * @param expression Call expression associated with the call statement
     * @return Call statement node
     */
    private STNode parseCallStatement(STNode expression) {
        STNode semicolon = parseSemicolon();
        return STNodeFactory.createCallStatement(expression, semicolon);
    }

    private STNode parseCallStatementWithCheck() {
        startContext(ParserRuleContext.CALL_STMT);
        STNode checkingKeyword = parseCheckingKeyword();
        STNode expr = parseExpression();
        validateExprInCallStatement(checkingKeyword, expr);

        STNode checkExpr = STNodeFactory.createCheckExpression(checkingKeyword, expr);
        STNode checkStmt = parseCallStatement(checkExpr);
        endContext();
        return checkStmt;
    }

    /**
     * Validate the call-expression in the call statement. Call expression takes the following structure.
     * <p>
     * <code>call-expr := function-call-expr | method-call-expr | checking-keyword call-expr</code>
     * 
     * @param checkingKeyword Checking keyword observed before the expression.
     * @param expr Expression followed by the checking keyword
     */
    private void validateExprInCallStatement(STNode checkingKeyword, STNode expr) {
        switch (expr.kind) {
            case FUNCTION_CALL:
            case METHOD_CALL:
                break;
            case CHECK_EXPRESSION:
                // Recursively validate
                STCheckExpression checkExpr = (STCheckExpression) expr;
                validateExprInCallStatement(checkExpr.checkingKeyword, checkExpr.rhsExpr);
                break;
            default:
                if (isMissingNode(expr)) {
                    break;
                }

                // TODO:
                this.errorHandler.reportInvalidNode(null,
                        "expression followed by the '" + checkingKeyword.toString().trim() +
                                "' keyword must be a func-call, a method-call or a check-expr");
                break;
        }
    }

    /**
     * Check whether a node is a missing node.
     * 
     * @param node Node to check
     * @return <code>true</code> if the node is a missing node. <code>false</code> otherwise
     */
    private boolean isMissingNode(STNode node) {
        return node instanceof STMissingToken;
    }

    /**
     * Parse check expression.
     * <p>
     * <code>
     * checking-expr := checking-keyword expression
     * </code>
     * 
     * @return Check expression node
     */
    private STNode parseCheckExpression() {
        STNode checkingKeyword = parseCheckingKeyword();
        STNode expr = parseExpression(OperatorPrecedence.UNARY, false);
        return STNodeFactory.createCheckExpression(checkingKeyword, expr);
    }

    /**
     * Parse checking keyword.
     * <p>
     * <code>
     * checking-keyword := check | checkpanic
     * </code>
     * 
     * @return Parsed node
     */
    private STNode parseCheckingKeyword() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CHECK_KEYWORD || token.kind == SyntaxKind.CHECKPANIC_KEYWORD) {
            return consume();
        } else {
            Solution sol = recover(token, ParserRuleContext.CHECKING_KEYWORD);
            return sol.recoveredNode;
        }
    }
}
