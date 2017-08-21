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
import ASTFactory from './ast-factory';
import PackageDefinition from './package-definition';
import SourceGenVisitor from '../visitors/source-gen/ballerina-ast-root-visitor';

class BallerinaASTDeserializer {
    /**
     * deserialize to the AST model from source
     * @param {object} data - ast model data
     * @param {object} file - file object
     * @returns {BallerinaASTRoot} - ballerina ast root
     */
    static getASTModel(data, file) {
        const astRoot = deserializeNode(data.root, file);
        if (!_.isNil(data.whitespace_descriptor)) {
            astRoot.setWhiteSpaceDescriptor(data.whitespace_descriptor);
            astRoot.whiteSpace.useDefault = false;
            const packageNodes = astRoot.getChildrenOfType((node) => {
                return node instanceof PackageDefinition;
            });
            if (!_.isNil(packageNodes[0]) && !_.isNil(packageNodes[0].getPackageName())) {
                packageNodes[0].whiteSpace.useDefault = false;
            }
        }

        const sourceGen = new SourceGenVisitor();
        astRoot.accept(sourceGen);
        return astRoot;
    }
}

function deserializeNode(node, file) {
    const astRoot = ASTFactory.createBallerinaAstRoot();
    astRoot.setFile(file);

    _.each(node, (childNode) => {
        const child = ASTFactory.createFromJson(childNode);
        astRoot.addChild(child);
        child.initFromJson(childNode);
    });
    return astRoot;
}


export default BallerinaASTDeserializer;
