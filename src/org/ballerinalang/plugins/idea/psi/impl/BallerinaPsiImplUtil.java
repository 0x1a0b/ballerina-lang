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

package org.ballerinalang.plugins.idea.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.antlr.jetbrains.adaptor.psi.Trees;
import org.antlr.jetbrains.adaptor.xpath.XPath;
import org.ballerinalang.plugins.idea.BallerinaFileType;
import org.ballerinalang.plugins.idea.BallerinaLanguage;
import org.ballerinalang.plugins.idea.psi.ArgumentListNode;
import org.ballerinalang.plugins.idea.psi.ExpressionNode;
import org.ballerinalang.plugins.idea.psi.FunctionDefinitionNode;
import org.ballerinalang.plugins.idea.psi.FunctionInvocationStatementNode;
import org.ballerinalang.plugins.idea.psi.ParameterNode;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BallerinaPsiImplUtil {

    public static PsiElement findPackageNameReference(PsiNamedElement element) {
        Collection<? extends PsiElement> declarations =
                XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
                        "/compilationUnit/importDeclaration/packagePath/packageName/Identifier");
        String id = element.getName();
        PsiElement resolvedElement = Trees.toMap(declarations).get(id);

        if (resolvedElement == null) {
            declarations = XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
                    "/compilationUnit/packageDeclaration/packagePath/packageName/Identifier");
            resolvedElement = Trees.toMap(declarations).get(id);
        }
        return resolvedElement;
    }

    public static PsiElement findFunctionReference(PsiNamedElement element) {

        Project project = element.getProject();

        String packageName = element.getParent().getFirstChild().getText();
        String functionName = element.getText();

        // Get all files which matches the filename
        Collection<VirtualFile> fileList = FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME,
                BallerinaFileType.INSTANCE, GlobalSearchScope.allScope(project));

        // If no files found, return null
        if (fileList.isEmpty()) {
            return null;
        }

        // For each file
        for (VirtualFile virtualFile : fileList) {
            // Get the psi file
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);

            Collection<? extends PsiElement> packageDefinition =
                    XPath.findAll(BallerinaLanguage.INSTANCE, psiFile,
                            "/compilationUnit/packageDeclaration/packagePath/packageName/Identifier");
            PsiElement resolvedElement = Trees.toMap(packageDefinition).get(packageName);

            if (resolvedElement == null) {
                continue;
            }

            // Find all function definitions in the file
            Collection<? extends PsiElement> declarations =
                    XPath.findAll(BallerinaLanguage.INSTANCE, psiFile, "/compilationUnit/functionDefinition");

            // For each function definition
            for (PsiElement declaration : declarations) {

                // Get the function name
                Collection<? extends PsiElement> functionNames =
                        XPath.findAll(BallerinaLanguage.INSTANCE, declaration, "/functionDefinition/Identifier");
                resolvedElement = Trees.toMap(functionNames).get(functionName);
                // If the resolvedElement is null, continue with the next function definition
                if (resolvedElement == null) {
                    continue;
                }
                // If the function names does not match, continue with the next function definition
                if (!functionName.equals(resolvedElement.getText())) {
                    continue;
                }

                // Get the expression list
                Collection<ParameterNode> paramList = PsiTreeUtil.findChildrenOfType(declaration, ParameterNode.class);

                PsiElement[] children = getFunctionInvocationStatement(element).getChildren()[1].getChildren()[1]
                        .getChildren();

                //                Collection<ExpressionNode> argList = PsiTreeUtil.findChildrenOfType(
                //                        getFunctionInvocationStatement(element), ExpressionNode.class);
                //arg list
                if (paramList.size() == children.length / 2 + 1) {
                    return resolvedElement;
                }
            }
            //            PsiElement resolvedElement = Trees.toMap(declarations).get(functionName);
            //            if (resolvedElement != null) {
            //                return resolvedElement;
            //            }

            //            Collection<FunctionDefinitionNode> functions = PsiTreeUtil.findChildrenOfType(psiFile,
            //                    FunctionDefinitionNode.class);
            //
            //            for (FunctionDefinitionNode functionDefinitionNode : functions) {
            //                String name = functionDefinitionNode.get.getText();
            //
            //                if (!name.equals(functionName)) {
            //                    continue;
            //                }
            //
            //
            //
            //            }


        }

        return null;
        //        return PsiManager.getInstance(project).findFile(fileList.get(0));

        //        Collection<? extends PsiElement> declarations =
        //                XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
        //                        "/compilationUnit/importDeclaration/packagePath/packageName/Identifier");
        //        String id = element.getName();
        //        PsiElement resolvedElement = Trees.toMap(declarations).get(id);
        //
        //        if (resolvedElement == null) {
        //            declarations = XPath.findAll(BallerinaLanguage.INSTANCE, element.getContainingFile(),
        //                    "/compilationUnit/packageDeclaration/packagePath/packageName/Identifier");
        //            resolvedElement = Trees.toMap(declarations).get(id);
        //        }
        //        return resolvedElement;
    }

    public static PsiElement getFunctionInvocationStatement(PsiElement element) {
        PsiElement parent = element;
        while (!(parent instanceof FunctionInvocationStatementNode || parent instanceof ExpressionNode)
                && parent != null) {
            parent = parent.getParent();
        }
        return parent;
    }
}
