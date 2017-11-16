/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.docgen.docs;

import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.docgen.docs.html.HtmlDocumentWriter;
import org.ballerinalang.docgen.docs.utils.BallerinaDocUtils;
import org.ballerinalang.model.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerinalang.compiler.Compiler;
import org.wso2.ballerinalang.compiler.PackageLoader;
import org.wso2.ballerinalang.compiler.semantics.analyzer.CodeAnalyzer;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SemanticAnalyzer;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.compiler.util.Names;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.ballerinalang.compiler.CompilerOptionName.COMPILER_PHASE;
import static org.ballerinalang.compiler.CompilerOptionName.PRESERVE_WHITESPACE;
import static org.ballerinalang.compiler.CompilerOptionName.SOURCE_ROOT;

/**
 * Main class to generate a ballerina documentation.
 */
public class BallerinaDocGenerator {

    private static final Logger log = LoggerFactory.getLogger(BallerinaDocGenerator.class);
    private static final PrintStream out = System.out;

    private static final String BSOURCE_FILE_EXT = ".bal";
    private static final Path BAL_BUILTIN = Paths.get("ballerina/builtin");
    private static final Path BAL_BUILTIN_CORE = Paths.get("ballerina/builtin/core");

    /**
     * API to generate Ballerina API documentation.
     * 
     * @param output path to the output directory where the API documentation will be written to.
     * @param packageFilter comma separated list of package names to be filtered from the documentation.
     * @param sources either the path to the directories where Ballerina source files reside or a path to a Ballerina
     *            file which does not belong to a package.
     */
    public static void generateApiDocsWithFilter(String output, String packageFilter, String... sources) {
        generateApiDocs(output, packageFilter, false, sources);
    }
    
    /**
     * API to generate Ballerina API documentation for Native Source.
     * 
     * @param output path to the output directory where the API documentation will be written to.
     * @param packageFilter comma separated list of package names to be filtered from the documentation.
     * @param sources either the path to the directories where Ballerina source files reside or a path to a Ballerina
     *            file which does not belong to a package.
     */
    public static void generateNativeApiDocs(String output, String packageFilter, String... sources) {
        generateApiDocs(output, packageFilter, true, sources);
    }
    
    /**
     * API to generate Ballerina API documentation.
     * 
     * @param output path to the output directory where the API documentation will be written to.
     * @param packageFilter comma separated list of package names to be filtered from the documentation.
     * @param isNative whether the given packages are native or not.
     * @param sources either the path to the directories where Ballerina source files reside or a path to a Ballerina
     *            file which does not belong to a package.
     */
    public static void generateApiDocs(String output, String packageFilter, boolean isNative, String... sources) {
        out.println("docerina: API documentation generation for sources - " + Arrays.toString(sources));
        HtmlDocumentWriter htmlDocumentWriter;
        if (output == null) {
            htmlDocumentWriter = new HtmlDocumentWriter();
        } else {
            htmlDocumentWriter = new HtmlDocumentWriter(output);
        }
        for (String source : sources) {
            try {
                Map<String, BLangPackage> docsMap;

                if (source.endsWith(".bal")) {
                    Path sourceFilePath = Paths.get(source);
                    Path parentDir = sourceFilePath.getParent();
                    Path fileName = sourceFilePath.getFileName();

                    if (parentDir == null || fileName == null) {
                        log.warn("Skipping the source generation for invalid path: " + sourceFilePath);
                        continue;
                    }

                    docsMap = generatePackageDocsFromBallerina(parentDir.toString(), fileName, packageFilter, isNative);
                } else {
                    Path dirPath = Paths.get(source);
                    docsMap = generatePackageDocsFromBallerina(dirPath.toString(), dirPath, packageFilter, isNative);
                }
                htmlDocumentWriter.write(docsMap.values());
            } catch (IOException e) {
                out.println(String.format("docerina: API documentation generation failed for %s: %s", source,
                        e.getMessage()));
                log.error(String.format("API documentation generation failed for %s", source), e);
            }
        }
    }

    /**
     * Generates {@link BLangPackage} objects for each Ballerina package from the given ballerina files.
     *
     * @param sourceRoot points to the folder relative to which package path is given
     * @param packagePath should point either to a ballerina file or a folder with ballerina files.
     * @return a map of {@link BLangPackage} objects. Key - Ballerina package name Value - {@link BLangPackage}
     */
    protected static Map<String, BLangPackage> generatePackageDocsFromBallerina(String sourceRoot, String packagePath)
            throws IOException {
        return generatePackageDocsFromBallerina(sourceRoot, packagePath, null);
    }

    /**
     * Generates {@link BLangPackage} objects for each Ballerina package from the given ballerina files.
     *
     * @param sourceRoot    points to the folder relative to which package path is given
     * @param packagePath   should point either to a ballerina file or a folder with ballerina files.
     * @param packageFilter comma separated list of package names/patterns to be filtered from the documentation.
     * @return a map of {@link BLangPackage} objects. Key - Ballerina package name Value - {@link BLangPackage}
     */
    protected static Map<String, BLangPackage> generatePackageDocsFromBallerina(String sourceRoot, String packagePath,
                                                                                String packageFilter)
            throws IOException {
        return generatePackageDocsFromBallerina(sourceRoot, packagePath, packageFilter, false);
    }

    /**
     * Generates {@link BLangPackage} objects for each Ballerina package from the given ballerina files.
     *
     * @param sourceRoot    points to the folder relative to which package path is given
     * @param packagePath   should point either to a ballerina file or a folder with ballerina files.
     * @param packageFilter comma separated list of package names/patterns to be filtered from the documentation.
     * @return a map of {@link BLangPackage} objects. Key - Ballerina package name Value - {@link BLangPackage}
     */
    protected static Map<String, BLangPackage> generatePackageDocsFromBallerina(
            String sourceRoot, String packagePath, String packageFilter, boolean isNative) throws IOException {
        return generatePackageDocsFromBallerina(sourceRoot, Paths.get(packagePath), packageFilter, isNative);
    }

    /**
     * Generates {@link BLangPackage} objects for each Ballerina package from the given ballerina files.
     *
     * @param sourceRoot points to the folder relative to which package path is given
     * @param packagePath   a {@link Path} object pointing either to a ballerina file or a folder with ballerina files.
     * @param packageFilter comma separated list of package names/patterns to be filtered from the documentation.
     * @param isNative      whether the given packages are native or not.
     * @return a map of {@link BLangPackage} objects. Key - Ballerina package name Value - {@link BLangPackage}
     */
    protected static Map<String, BLangPackage> generatePackageDocsFromBallerina(
            String sourceRoot, Path packagePath, String packageFilter, boolean isNative) throws IOException {
        final List<Path> packagePaths = new ArrayList<>();
        if (Files.isDirectory(packagePath)) {
            BallerinaSubPackageVisitor subPackageVisitor = new BallerinaSubPackageVisitor(packagePath, packagePaths);
            Files.walkFileTree(packagePath, subPackageVisitor);
        } else {
            packagePaths.add(packagePath);
        }

        BallerinaDocDataHolder dataHolder = BallerinaDocDataHolder.getInstance();
        if (!isNative) {
            // This is necessary to be true in order to Ballerina to work properly
            System.setProperty("skipNatives", "true");
        }

        BLangPackage bLangPackage;
        
        for (Path path : packagePaths) {
            CompilerContext context = new CompilerContext();
            CompilerOptions options = CompilerOptions.getInstance(context);
            options.put(SOURCE_ROOT, sourceRoot);
            options.put(COMPILER_PHASE, CompilerPhase.DESUGAR.toString());
            options.put(PRESERVE_WHITESPACE, "false");

            Compiler compiler = Compiler.getInstance(context);

            // TODO: Remove this and the related constants once these are properly handled in the core
            if (BAL_BUILTIN.equals(path) || BAL_BUILTIN_CORE.equals(path)) {
                bLangPackage = loadBuiltInPackage(context);
            } else {
                // compile the given file
                compiler.compile(getPackageNameFromPath(path));
                bLangPackage = (BLangPackage) compiler.getAST();
            }

            if (bLangPackage == null) {
                out.println(String.format("docerina: invalid Ballerina package: %s", packagePath));
            } else {
                String packageName = bLangPackage.symbol.pkgID.name.value;
                if (isFilteredPackage(packageName, packageFilter)) {
                    if (BallerinaDocUtils.isDebugEnabled()) {
                        out.println("Package " + packageName + " excluded");
                    }
                    continue;
                }
                dataHolder.getPackageMap().put(packageName, bLangPackage);
            }
        }
        return dataHolder.getPackageMap();
    }

    private static boolean isFilteredPackage(String packageName, String packageFilter) {
        if ((packageFilter != null) && (packageFilter.trim().length() > 0)) {
            return Arrays.asList(packageFilter.split(",")).stream()
                    .filter(e -> packageName.startsWith(e.replace(".*", ""))).findAny().isPresent();
        }
        return false;
    }

    /**
     * Visits sub folders of a ballerina package.
     */
    static class BallerinaSubPackageVisitor extends SimpleFileVisitor<Path> {
        private Path source;
        private List<Path> subPackages;

        public BallerinaSubPackageVisitor(Path source, List<Path> aList) {
            this.source = source;
            this.subPackages = aList;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file.toString().endsWith(BSOURCE_FILE_EXT)) {
                Path relativePath = source.relativize(file.getParent());
                if (!subPackages.contains(relativePath)) {
                    subPackages.add(relativePath);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        public List<Path> getSubPackages() {
            return subPackages;
        }
    }

    private static String getPackageNameFromPath(Path path) {
        StringJoiner sj = new StringJoiner(".");
        Iterator<Path> pathItr = path.iterator();
        while (pathItr.hasNext()) {
            sj.add(pathItr.next().toString());
        }
        return sj.toString();
    }

    private static BLangPackage loadBuiltInPackage(CompilerContext context) {
        BLangPackage builtInCorePkg = getBuiltInPackage(context, Names.BUILTIN_CORE_PACKAGE);
        SymbolTable symbolTable = SymbolTable.getInstance(context);
        symbolTable.createErrorTypes();
        symbolTable.loadOperators();
        // Load built-in packages.
        BLangPackage builtInPkg = getBuiltInPackage(context, Names.BUILTIN_PACKAGE);
        builtInCorePkg.getStructs().forEach(s -> {
            builtInPkg.getStructs().add(s);
            builtInPkg.topLevelNodes.add(s);
        });
        symbolTable.builtInPackageSymbol = builtInPkg.symbol;
        return builtInPkg;
    }

    private static BLangPackage getBuiltInPackage(CompilerContext context, Name name) {
        PackageLoader pkgLoader = PackageLoader.getInstance(context);
        SemanticAnalyzer semAnalyzer = SemanticAnalyzer.getInstance(context);
        CodeAnalyzer codeAnalyzer = CodeAnalyzer.getInstance(context);
        return codeAnalyzer.analyze(semAnalyzer.analyze(pkgLoader.loadEntryPackage(name.getValue())));
    }
}
