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

package org.ballerinalang.plugins.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PathUtil;
import org.ballerinalang.plugins.idea.psi.FullyQualifiedPackageNameNode;
import org.ballerinalang.plugins.idea.psi.PackageDeclarationNode;
import org.ballerinalang.plugins.idea.sdk.BallerinaSdkService;
import org.ballerinalang.util.diagnostic.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

/**
 * An external annotator is an object that analyzes code in a document
 * and annotates the PSI elements with errors or warnings. Because such
 * analysis can be expensive, we don't want it in the GUI event loop. Jetbrains
 * provides this external annotator mechanism to run these analyzers out of band.
 */
public class BallerinaExternalAnnotator extends ExternalAnnotator<BallerinaExternalAnnotator.Data, List<Diagnostic>> {

    // NOTE: can't use instance vars as only 1 instance.
    private static Method method;
    private static URLClassLoader urlClassLoader;
    private static Editor editor;

    private static final Logger LOGGER = LoggerFactory.getLogger(BallerinaExternalAnnotator.class);

    /**
     * Called first.
     */
    @Override
    @Nullable
    public Data collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        BallerinaExternalAnnotator.editor = editor;
        VirtualFile virtualFile = file.getVirtualFile();

        String packageNameNode = getPackageName(file);
        if (method == null) {
            Module module = ModuleUtilCore.findModuleForFile(virtualFile, file.getProject());
            if (module == null) {
                return null;
            }
            Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
            String sdkHome;
            if (moduleSdk != null) {
                sdkHome = moduleSdk.getHomePath();
            } else {
                sdkHome = BallerinaSdkService.getInstance(file.getProject()).getSdkHomePath(null);
            }

            try {
                List<URL> filesToLoad = new LinkedList<>();
                File[] files = new File(sdkHome + "/bre/lib").listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().endsWith(".jar")) {
                            filesToLoad.add(f.toURI().toURL());
                        }
                    }
                }
                urlClassLoader = new URLClassLoader(filesToLoad.toArray(new URL[filesToLoad.size()]),
                        this.getClass().getClassLoader());
                Class classToLoad = Class.forName("org.ballerinalang.launcher.BTester", true, urlClassLoader);
                method = classToLoad.getMethod("getDiagnostics", ClassLoader.class, String.class, String.class);
            } catch (MalformedURLException | NoSuchMethodException | ClassNotFoundException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
        return new Data(editor, file, packageNameNode);
    }

    /**
     * Called 2nd; look for trouble in file and return list of issues.
     * <p>
     * For most custom languages, you would not reimplement your semantic
     * analyzer using PSI trees. Instead, here is where you would call out to
     * your custom languages compiler or interpreter to get error messages
     * or other bits of information you'd like to annotate the document with.
     */
    @Nullable
    @Override
    public List<Diagnostic> doAnnotate(final Data data) {
        if (method != null) {
            ApplicationManager.getApplication().invokeAndWait(() -> {
                FileDocumentManager.getInstance().saveAllDocuments();
            });

            Module module = ModuleUtilCore.findModuleForPsiElement(data.psiFile);
            String sourceRoot = data.psiFile.getProject().getBasePath();

            String fileName = data.packageNameNode;
            if (fileName == null) {
                VirtualFile virtualFile = data.psiFile.getVirtualFile();
                fileName = virtualFile.getName();
            }
            if (module != null && FileUtil.exists(module.getModuleFilePath())) {
                sourceRoot = StringUtil.trimEnd(PathUtil.getParentPath(module.getModuleFilePath()), ".idea");
            }

            try {
                return (List<Diagnostic>) method.invoke(null, urlClassLoader, sourceRoot, fileName);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
        return new LinkedList<>();
    }

    private String getPackageName(PsiFile file) {
        PackageDeclarationNode packageDeclarationNode = PsiTreeUtil.findChildOfType(file,
                PackageDeclarationNode.class);
        if (packageDeclarationNode == null) {
            return null;
        }
        FullyQualifiedPackageNameNode packageNameNode = PsiTreeUtil.getChildOfType(packageDeclarationNode,
                FullyQualifiedPackageNameNode.class);
        if (packageNameNode == null) {
            return null;
        }
        return packageNameNode.getText();
    }

    /**
     * Called 3rd to actually annotate the editor window.
     */
    @Override
    public void apply(@NotNull PsiFile file, List<Diagnostic> diagnostics, @NotNull AnnotationHolder holder) {
        String packageName = getPackageName(file);
        String fileName = file.getVirtualFile().getName();

        for (Diagnostic diagnostic : diagnostics) {
            // Validate the package name.
            if (packageName != null && !diagnostic.getSource().getPackageName().equals(packageName)) {
                continue;
            }
            // Validate the file name since diagnostics are sent for all files in the package.
            if (!fileName.equals(diagnostic.getSource().getCompilationUnitName())) {
                continue;
            }

            Diagnostic.DiagnosticPosition position = diagnostic.getPosition();
            // If the start line or start column is less than 0, it will throw an exception.
            if (position.getStartLine() <= 0 || position.getStartColumn() <= 0) {
                continue;
            }

            // Get the logical start postion. This is used to get the offset.
            LogicalPosition startPosition = new LogicalPosition(position.getStartLine() - 1,
                    position.getStartColumn() - 1);
            int startOffset = editor.logicalPositionToOffset(startPosition);
            // Get the element at the offset.
            PsiElement elementAtOffset = file.findElementAt(startOffset);
            // If the element at the offset is a whitespace, highlight the next element.
            if (elementAtOffset instanceof PsiWhiteSpace) {
                elementAtOffset = PsiTreeUtil.nextVisibleLeaf(elementAtOffset);
            }

            // Get the text range to be highlighted.
            TextRange textRange;
            if (elementAtOffset == null) {
                int endColumn = position.getStartColumn() == position.getEndColumn() ?
                        position.getEndColumn() + 1 : position.getEndColumn();
                if (position.getEndLine() <= 0) {
                    continue;
                }
                LogicalPosition endPosition = new LogicalPosition(position.getEndLine() - 1, endColumn);
                int endOffset = editor.logicalPositionToOffset(endPosition);
                textRange = new TextRange(startOffset, endOffset);
            } else {
                int endOffset = elementAtOffset.getTextOffset() + elementAtOffset.getTextLength();
                textRange = new TextRange(elementAtOffset.getTextOffset(), endOffset);
            }

            // Highlight the range according to the diagnostic kind.
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                holder.createErrorAnnotation(textRange, diagnostic.getMessage());
            } else if (diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                holder.createWarningAnnotation(textRange, diagnostic.getMessage());
            } else if (diagnostic.getKind() == Diagnostic.Kind.NOTE) {
                holder.createInfoAnnotation(textRange, diagnostic.getMessage());
            }
        }
    }

    public static class Data {

        Editor editor;
        PsiFile psiFile;
        String packageNameNode;

        public Data(@NotNull Editor editor, @NotNull PsiFile psiFile, @Nullable String packageNameNode) {
            this.editor = editor;
            this.psiFile = psiFile;
            this.packageNameNode = packageNameNode;
        }
    }
}
