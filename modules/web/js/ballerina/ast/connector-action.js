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
import ASTNode from './node';
import CommonUtils from '../utils/common-utils';
import ASTFactory from './../ast/ballerina-ast-factory';

/**
 * Constructor for ConnectorAction
 * @param {object} args - Constructor arguments
 * @constructor
 */
class ConnectorAction extends ASTNode {
    constructor(args) {
        super('ConnectorAction');
        this.action_name = _.get(args, 'action_name');
        this.arguments = _.get(args, 'arguments', []);
        this.whiteSpace.defaultDescriptor.regions = {
            0: ' ',
            1: ' ',
            2: ' ',
            3: ' ',
            4: ' ',
            5: '\n',
            6: '\n',
        };
    }

    /**
     * Get the name of action
     * @return {string} action_name - Action Name
     */
    getActionName() {
        return this.action_name;
    }

    /**
     * Get the action Arguments
     * @return {Object[]} arguments - Action Arguments
     */
    getArguments() {
        return this.getArgumentParameterDefinitionHolder().getChildren();
    }

    getArgumentParameterDefinitionHolder() {
        let argParamDefHolder = this.findChild(this.getFactory().isArgumentParameterDefinitionHolder);
        if (_.isUndefined(argParamDefHolder)) {
            argParamDefHolder = this.getFactory().createArgumentParameterDefinitionHolder();
            this.addChild(argParamDefHolder, undefined, true, true, false);
        }
        return argParamDefHolder;
    }

    /**
     * Set the Action name
     * @param {string} name - Action Name
     * @param {object} options - attribute options
     */
    setActionName(name, options) {
        this.setAttribute('action_name', name, options);
    }

    /**
     * Set the Action name
     * This is an alias to setActionName
     * Many other node types' setters for name takes pattern `set<Node Type>Name`
     * Node type of connector actions is ConnectorAction
     * This method helps keep that formality so we can take advantage of it
     * @param {string} name - Action Name
     * @param {object} options - attribute options
     */
    setConnectorActionName(name, options) {
        this.setActionName(name, options);
    }

    /**
     * Get the variable Declarations
     * @return {VariableDeclaration[]} variableDeclarations
     */
    getVariableDefinitionStatements() {
        const variableDefinitionStatements = [];
        const self = this;

        _.forEach(this.getChildren(), (child) => {
            if (self.getFactory().isVariableDefinitionStatement(child)) {
                variableDefinitionStatements.push(child);
            }
        });
        return variableDefinitionStatements;
    }

    /**
     * Get the namespace declaration statements.
     * @return {ASTNode[]} namespace declaration statements
     * */
    getNamespaceDeclarationStatements() {
        return this.filterChildren(this.getFactory().isNamespaceDeclarationStatement).slice(0);
    }

    /**
     * Remove variable declaration.
     * @param {string} variableDeclarationIdentifier - variable declaration's identofier
     */
    removeVariableDeclaration(variableDeclarationIdentifier) {
        const self = this;
        // Removing the variable from the children.
        const variableDeclarationChild = _.find(this.getChildren(), (child) => {
            return self.getFactory().isVariableDeclaration(child)
                && child.getIdentifier() === variableDeclarationIdentifier;
        });
        this.removeChild(variableDeclarationChild);
    }

    /**
     * Adds new variable declaration.
     * @param {VariableDeclaration} newVariableDeclaration - new variable declaration
     */
    addVariableDeclaration(newVariableDeclaration) {
        const self = this;
        // Get the index of the last variable declaration.
        let index = _.findLastIndex(this.getChildren(), (child) => {
            return self.getFactory().isVariableDeclaration(child);
        });

        // index = -1 when there are not any variable declarations, hence get the index for connector
        // declarations.
        if (index === -1) {
            index = _.findLastIndex(this.getChildren(), (child) => {
                return self.getFactory().isConnectorDeclaration(child);
            });
        }

        this.addChild(newVariableDeclaration, index + 1);
    }

    // Start of return type functions.

    /**
     * Gets the return type as a string separated by commas.
     * @return {string} - Return types.
     */
    getReturnTypesAsString() {
        let returnTypesString = '';
        this.getReturnTypes().forEach((returnType, index) => {
            if (index !== 0) {
                returnTypesString += ',';
            }
            returnTypesString += (returnType.whiteSpace.useDefault && index !== 0 ? ' '
                                : returnType.getWSRegion(0));
            returnTypesString += returnType.getParameterDefinitionAsString();
        });
        return returnTypesString;
    }

    /**
     * Gets the defined return types.
     * @return {ParameterDefinition[]} - Array of return arguments.
     */
    getReturnTypes() {
        return this.getReturnParameterDefinitionHolder().getChildren();
    }

    getReturnParameterDefinitionHolder() {
        let returnParamDefHolder = this.findChild(this.getFactory().isReturnParameterDefinitionHolder);
        if (_.isUndefined(returnParamDefHolder)) {
            returnParamDefHolder = this.getFactory().createReturnParameterDefinitionHolder();
            this.addChild(returnParamDefHolder, undefined, true, true, false);
        }
        return returnParamDefHolder;
    }

    /**
     * Adds a new argument to return type model.
     * @param {string} type - The type of the argument.
     * @param {string} identifier - The identifier of the argument.
     */
    addReturnType(type, identifier) {
        const returnParamDefHolder = this.getReturnParameterDefinitionHolder();

        // Adding return type mode if it doesn't exists.
        if (_.isUndefined(this.getReturnTypes())) {
            this.addChild(this.getFactory().createReturnType());
        }

        // Check if there is already a return type with the same identifier.
        if (!_.isUndefined(identifier)) {
            const child = returnParamDefHolder.filterChildren((filterChild) => {
                return filterChild.getName() === identifier;
            });
            if (_.isUndefined(child)) {
                const errorString = "An return argument with identifier '" + identifier + "' already exists.";
                log.error(errorString);
                throw errorString;
            }
        }

        // Validating whether return type can be added based on identifiers of other return types.
        if (!_.isUndefined(identifier)) {
            if (!this.hasNamedReturnTypes() && this.hasReturnTypes()) {
                const errorStringWithoutIdentifiers = 'Return types without identifiers already exists. ' +
                    'Remove them to add return types with identifiers.';
                log.error(errorStringWithoutIdentifiers);
                throw errorStringWithoutIdentifiers;
            }
        } else if (this.hasNamedReturnTypes() && this.hasReturnTypes()) {
            const errorStringWithIdentifiers = 'Return types with identifiers already exists. Remove them to add ' +
                    'return types without identifiers.';
            log.error(errorStringWithIdentifiers);
            throw errorStringWithIdentifiers;
        }

        const paramDef = this.getFactory().createParameterDefinition({ typeName: type, name: identifier });
        returnParamDefHolder.addChild(paramDef, 0);
    }

    /**
     * Returns Whether has named return types or not
     * @return {boolean} - true|false
     */
    hasNamedReturnTypes() {
        if (this.getReturnParameterDefinitionHolder().getChildren().length === 0) {
            // if there are no return types in the return type model
            return false;
        }
            // check if any of the return types have identifiers
        const indexWithoutIdentifiers = _.findIndex(this.getReturnParameterDefinitionHolder().getChildren(),
            (child) => {
                return _.isUndefined(child.getName());
            });

        return indexWithoutIdentifiers === -1;
    }

    /**
     * Returns Whether has return types or not
     * @return {boolean} - true|false
     */
    hasReturnTypes() {
        return this.getReturnParameterDefinitionHolder().getChildren().length > 0;
    }

    /**
     * Removes return type argument from the return type model.
     * @param {string} modelID - The id of an {Argument} which resides in the return type model.
     */
    removeReturnType(modelID) {
        const removeChild = this.getReturnParameterDefinitionHolder()
            .removeChildById(this.getFactory().isParameterDefinition, modelID);

        // Deleting the argument from the AST.
        if (_.isUndefined(removeChild)) {
            const exceptionString = 'Could not find a return type with id : ' + modelID;
            log.error(exceptionString);
            throw exceptionString;
        }
    }

    // End of return type functions.

    /**
     * Returns the list of arguments as a string separated by commas.
     * @return {string} - Arguments as string.
     */
    getArgumentsAsString() {
        let argsString = '';
        this.getArgumentParameterDefinitionHolder().getChildren().forEach((arg, index) => {
            if (index !== 0) {
                argsString += ',';
            }
            argsString += (arg.whiteSpace.useDefault && index !== 0 ? ' ' : arg.getWSRegion(0));
            argsString += arg.getParameterDefinitionAsString();
        });
        return argsString;
    }

    /**
     * Adds new argument to the connector action.
     * @param {string} type - The type of the argument.
     * @param {string} identifier - The identifier of the argument.
     */
    addArgument(type, identifier) {
        // creating argument
        const newArgumentParamDef = this.getFactory().createParameterDefinition();
        newArgumentParamDef.setTypeName(type);
        newArgumentParamDef.setName(identifier);

        const argParamDefHolder = this.getArgumentParameterDefinitionHolder();
        const index = argParamDefHolder.getChildren().length;

        argParamDefHolder.addChild(newArgumentParamDef, index + 1);
    }

    /**
     * Removes an argument from a function definition.
     * @param {string} identifier - The identifier of the argument.
     */
    removeArgument(identifier) {
        const argParamDefHolder = this.getArgumentParameterDefinitionHolder();
        argParamDefHolder.removeChildByName(this.getFactory().isParameterDefinition, identifier);
    }

    /**
     * Get the connector declarations
     * @return {Array} an array of connector declarations
     */
    getConnectionDeclarations() {
        const connectorDeclaration = [];
        const self = this;

        _.forEach(this.getChildren(), (child) => {
            if (self.getFactory().isConnectorDeclaration(child)) {
                connectorDeclaration.push(child);
            }
        });
        return _.sortBy(connectorDeclaration, [function (connectorDecl) {
            return connectorDecl.getConnectorVariable();
        }]);
    }

    getWorkerDeclarations() {
        const workerDeclarations = [];
        const self = this;

        _.forEach(this.getChildren(), (child) => {
            if (self.getFactory().isWorkerDeclaration(child)) {
                workerDeclarations.push(child);
            }
        });
        return _.sortBy(workerDeclarations, [function (workerDeclaration) {
            return workerDeclaration.getWorkerName();
        }]);
    }

    /**
     * initialize ConnectorAction from json object
     * @param {Object} jsonNode to initialize from
     * @param {string} [jsonNode.resource_name] - Name of the resource definition
     */
    initFromJson(jsonNode) {
        const self = this;
        this.setActionName(jsonNode.action_name, { doSilently: true });

        _.each(jsonNode.children, (childNode) => {
            const child = self.getFactory().createFromJson(childNode);
            self.addChild(child, undefined, true, true);
            child.initFromJson(childNode);
        });
    }

    /**
     * Validates possible immediate child types.
     * @override
     * @param node
     * @return {boolean}
     */
    canBeParentOf(node) {
        return this.getFactory().isConnectorDeclaration(node)
            || this.getFactory().isVariableDeclaration(node)
            || this.getFactory().isWorkerDeclaration(node)
            || this.getFactory().isStatement(node);
    }

    /**
     * add a child to the action
     * @param {ASTNode} child - child node
     * @param {number} index - child index
     * @param {boolean} ignoreTreeModifiedEvent - whether ignore the tree modified event or not
     * @param {boolean} ignoreChildAddedEvent - whether ignore the child added event or not
     * @param {boolean} generateId - generate id or not
     */
    addChild(child, index, ignoreTreeModifiedEvent, ignoreChildAddedEvent, generateId) {
        if (ASTFactory.isWorkerDeclaration(child)) {
            Object.getPrototypeOf(this.constructor.prototype)
              .addChild.call(this, child, undefined, ignoreTreeModifiedEvent, ignoreChildAddedEvent, generateId);
        } else {
            const firstWorkerIndex = _.findIndex(this.getChildren(), (filteredChild) => {
                return ASTFactory.isWorkerDeclaration(filteredChild);
            });

            if (firstWorkerIndex > -1 && _.isNil(index)) {
                index = firstWorkerIndex;
            }
            Object.getPrototypeOf(this.constructor.prototype)
            .addChild.call(this, child, index, ignoreTreeModifiedEvent, ignoreChildAddedEvent, generateId);
        }
    }

    /**
     * @inheritDoc
     * @override
     */
    generateUniqueIdentifiers() {
        CommonUtils.generateUniqueIdentifier({
            node: this,
            attributes: [{
                defaultValue: 'Action',
                setter: this.setActionName,
                getter: this.getActionName,
                parents: [{
                    // ballerina-ast-node
                    node: this.parent,
                    getChildrenFunc: this.parent.getConnectorActionDefinitions,
                    getter: this.getActionName,
                }],
            }],
        });
    }

    /**
     * Get the connector by name
     * @param {string} connectorName - name of the connector
     * @return {ConnectorDeclaration} - connector declaration matching the connector name
     */
    getConnectorByName(connectorName) {
        const factory = this.getFactory();
        const connectorReference = _.find(this.getChildren(), (child) => {
            let connectorVariableName;
            if (factory.isAssignmentStatement(child) && factory.isConnectorInitExpression(child.getChildren()[1])) {
                const variableReferenceList = [];

                _.forEach(child.getChildren()[0].getChildren(), (variableRef) => {
                    variableReferenceList.push(variableRef.getExpressionString());
                });

                connectorVariableName = (_.join(variableReferenceList, ',')).trim();
            } else if (factory.isConnectorDeclaration(child)) {
                connectorVariableName = child.getConnectorVariable();
            }

            return connectorVariableName === connectorName;
        });

        return !_.isNil(connectorReference) ? connectorReference : this.getParent().getConnectorByName(connectorName);
    }

    /**
     * Get all the connector references in the immediate scope
     * @return {Array} connectorReferences
     */
    getConnectorsInImmediateScope() {
        const factory = this.getFactory();
        const connectorReferences = _.filter(this.getChildren(), (child) => {
            return factory.isConnectorDeclaration(child);
        });

        return !_.isEmpty(connectorReferences) ? connectorReferences : this.getParent().getConnectorsInImmediateScope();
    }
}

export default ConnectorAction;
