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
import AbstractSourceGenVisitor from './abstract-source-gen-visitor';
import AnnotationAttributeValueVisitor from './annotation-attribute-value-visitor';
import ASTFactory from '../../ast/ast-factory';

/**
 * Source gen visitor for annotation attribute visitor.
 *
 * @class AnnotationAttributeVisitor
 * @extends {AbstractSourceGenVisitor}
 */
class AnnotationAttributeVisitor extends AbstractSourceGenVisitor {

    /**
     * Constructor
     * @param {SourceGenVisitor} parent
     * @param {boolean} isFirstAttribute Indicates whether this is the first in list
     */
    constructor(parent, isFirstAttribute = false) {
        super(parent);
        this.isFirstAttribute = isFirstAttribute;
    }

    /**
     * Checks if the annotation attribute can be visited.
     *
     * @param {AnnotationAttribute} annotationAttribute The annotation attribute.
     * @returns {boolean} true if can be visited, else false.
     * @memberof AnnotationAttributeVisitor
     */
    canVisitAnnotationAttribute(annotationAttribute) {
        return annotationAttribute.getKey() !== undefined && annotationAttribute.getKey().trim() !== '';
    }

    /**
     * Begins visiting an annotation attribute.
     *
     * @param {AnnotationAttribute} annotationAttribute The node to be visited.
     * @memberof AnnotationAttributeVisitor
     */
    beginVisitAnnotationAttribute(annotationAttribute) {
        const useDefaultWS = annotationAttribute.whiteSpace.useDefault;
        if (useDefaultWS) {
            this.currentPrecedingIndentation = this.getCurrentPrecedingIndentation();
            this.replaceCurrentPrecedingIndentation(this.getIndentation());
        }
        let constructedSourceSegment = '';
        constructedSourceSegment += (useDefaultWS || this.isFirstAttribute
                                        ? '' : annotationAttribute.getWSRegion(0))
                                    + annotationAttribute.getKey()
                                    + annotationAttribute.getWSRegion(1) + ':'
                                    + annotationAttribute.getWSRegion(2);
        this.appendSource(constructedSourceSegment);
    }

    /**
     * Visits the body of the annotation attribute.
     * @memberof AnnotationAttributeVisitor
     */
    visitAnnotationAttribute() {
    }

    /**
     * Ends visiting an annotation attribute.
     *
     * @param {AnnotationAttribute} annotationAttribute The node which was visited.
     * @memberof AnnotationAttributeVisitor
     */
    endVisitAnnotationAttribute(annotationAttribute) {
        this.appendSource((annotationAttribute.whiteSpace.useDefault) ? this.currentPrecedingIndentation : '');
        this.getParent().appendSource(this.getGeneratedSource());
    }

    /**
     * Visits the annotationa attribute value.
     *
     * @param {AnnotationAttributeValue} annotationAttributeValue The annotation attribute value to be visited.
     * @memberof AnnotationAttributeVisitor
     */
    visitAnnotationAttributeValue(annotationAttributeValue) {
        // Visit annotation attribute value
        const annotationAttributeValueVisitor = new AnnotationAttributeValueVisitor(this);
        annotationAttributeValue.accept(annotationAttributeValueVisitor);
    }
}
export default AnnotationAttributeVisitor;
