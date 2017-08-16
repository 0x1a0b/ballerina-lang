/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import AbstractStatementSourceGenVisitor from './abstract-statement-source-gen-visitor';
import FunctionDefinitionVisitor from './function-definition-visitor';
import ASTFactory from '../../ast/ast-factory';

/**
 * Source generation for action invocation statement
 */
class ActionInvocationStatementVisitor extends AbstractStatementSourceGenVisitor {

    /**
     * Check can visit for action invocation expression
     * @return {boolean} true|false whether can visit or not
     */
    canVisitActionInvocationExpression() {
        return true;
    }

    /**
     * Check can visit for action invocation statement
     * @return {boolean} true|false whether can visit or not
     */
    canVisitActionInvocationStatement() {
        return true;
    }

    /**
     * Begin visit for action invocation Statement
     * @param {ActionInvocationStatement} actionInvocationStatement - action invocation statement ASTNode
     */
    beginVisitActionInvocationStatement(actionInvocationStatement) {
        if (actionInvocationStatement.whiteSpace.useDefault) {
            this.currentPrecedingIndentation = this.getCurrentPrecedingIndentation();
            this.replaceCurrentPrecedingIndentation(this.getIndentation());
        }
    }

    /**
     * Begin visit for action invocation Statement
     * @param {ActionInvocationExpression} actionInvocationExpr - action invocation expression ASTNode
     */
    beginVisitActionInvocationExpression(actionInvocationExpr) {
        // Calculate the line number
        const lineNumber = this.getTotalNumberOfLinesInSource() + 1;
        actionInvocationExpr.setLineNumber(lineNumber, { doSilently: true });

        const args = actionInvocationExpr.getArguments();

        let constructedSourceSegment = '';
        if (!_.isUndefined(actionInvocationExpr.getActionPackageName()) &&
            !_.isNil(actionInvocationExpr.getActionPackageName()) &&
            !_.isEqual(actionInvocationExpr.getActionPackageName(), 'Current Package')) {
            constructedSourceSegment = actionInvocationExpr.getActionPackageName()
                + actionInvocationExpr.getChildWSRegion('nameRef', 1) + ':'
                + actionInvocationExpr.getChildWSRegion('nameRef', 2);
        }

        constructedSourceSegment += actionInvocationExpr.getActionConnectorName() +
            actionInvocationExpr.getWSRegion(1) + '.' + actionInvocationExpr.getWSRegion(2) +
            actionInvocationExpr.getActionName() + actionInvocationExpr.getWSRegion(3)
            + '(' + actionInvocationExpr.getWSRegion(4);
        this.appendSource(constructedSourceSegment);

        for (let itr = 0; itr < args.length; itr++) {
            // TODO: we need to refactor actionInvocationExpr along with the action invocation argument types as well
            if (ASTFactory.isLambdaExpression(args[itr])) {
                const lambdaFn = args[itr].getLambdaFunction();
                lambdaFn.accept(new FunctionDefinitionVisitor(this));
            } else if (ASTFactory.isResourceParameter(args[itr])) {
                this.appendSource(args[itr].getParameterAsString());
            } else if (ASTFactory.isExpression(args[itr])) {
                this.appendSource(args[itr].getExpressionString());
            }
            if (itr !== args.length - 1) {
                this.appendSource(', ');
            }
        }

        constructedSourceSegment = ')' + actionInvocationExpr.getWSRegion(5);
        this.appendSource(constructedSourceSegment);
    }

    endVisitActionInvocationExpression(expression) {
        this.getParent().appendSource(this.getGeneratedSource());
        this.setGeneratedSource('');
    }

    /**
     * End visit for action invocation Statement
     * @param {ActionInvocationStatement} actionInvocationStatement - action invocation statement ASTNode
     */
    endVisitActionInvocationStatement(actionInvocationStatement) {
        this.appendSource(';' + actionInvocationStatement.getWSRegion(1));
        this.appendSource((actionInvocationStatement.whiteSpace.useDefault) ?
                      this.currentPrecedingIndentation : '');
        this.getParent().appendSource(this.getGeneratedSource());
    }
}

export default ActionInvocationStatementVisitor;
