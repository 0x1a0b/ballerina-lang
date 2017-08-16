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
import _ from 'lodash';
import log from 'log';
import Expression from './expression';
import ASTFactory from '../ast-factory.js';

/**
 * Constructor for BasicLiteralExpression
 * @param {Object} args - Arguments to create the BasicLiteralExpression
 * @constructor
 */
class LambdaExpression extends Expression {
    constructor(args) {
        super('LambdaExpression');
    }

    /**
     * setting parameters from json
     * @param jsonNode
     */
    initFromJson(jsonNode) {
        this.getChildren().length = 0;
        const self = this;
        _.each(jsonNode.children, (childNode) => {
            const child = ASTFactory.createFromJson(childNode);
            self.addChild(child, undefined, true, true);
            child.initFromJson(childNode);
        });
    }

    /**
     * @param {string} statementString
     * @param {[FunctionDefinition]} lambdaChildren
     */
    static replaceSymbol(statementString, lambdaChildren) {
        let i = 0;
        return statementString.replace(/ƒ/g,
            () => (lambdaChildren.length > i ? lambdaChildren[i++].getViewState().source : 'function(){}'));
    }

    setExpressionFromString(expression, callback) {
    }

    getExpressionString() {
        return 'ƒ';
    }

    /**
     *
     * @return {FunctionDefinition}
     */
    getLambdaFunction() {
        return this.getChildren()[0];
    }

}

export default LambdaExpression;
