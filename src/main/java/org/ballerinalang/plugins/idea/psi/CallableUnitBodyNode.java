/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

package org.ballerinalang.plugins.idea.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.antlr.jetbrains.adaptor.psi.ANTLRPsiNode;
import org.antlr.jetbrains.adaptor.psi.ScopeNode;
import org.ballerinalang.plugins.idea.psi.impl.BallerinaPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CallableUnitBodyNode extends ANTLRPsiNode implements ScopeNode {

    public CallableUnitBodyNode(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    @Override
    public PsiElement resolve(PsiNamedElement element) {
        if (element.getParent() instanceof NameReferenceNode
                || element.getParent() instanceof StatementNode) {

            FunctionInvocationNode functionInvocationNode = PsiTreeUtil.getParentOfType(element,
                    FunctionInvocationNode.class);
            if (functionInvocationNode == null) {
                PsiElement resolvedElement = BallerinaPsiImplUtil.resolveElement(this, element,
                        "//variableDefinitionStatement/Identifier");
                if (resolvedElement != null && !BallerinaPsiImplUtil.isStructField(element)) {
                    return resolvedElement;
                }
            }
            return BallerinaPsiImplUtil.resolveNameReferenceNode(this, element);
        } else if (element.getParent() instanceof VariableReferenceNode) {
            return BallerinaPsiImplUtil.resolveElement(this, element, "//variableDefinitionStatement/Identifier");
        } else if (element.getParent() instanceof TypeNameNode) {
            return BallerinaPsiImplUtil.resolveElement(this, element, "//functionDefinition/Identifier",
                    "//connectorDefinition/Identifier");
        }
        return null;
    }
}
