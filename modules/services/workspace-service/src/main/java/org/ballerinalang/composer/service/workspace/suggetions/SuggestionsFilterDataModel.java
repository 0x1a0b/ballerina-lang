/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package org.ballerinalang.composer.service.workspace.suggetions;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.Vocabulary;
import org.ballerinalang.composer.service.workspace.langserver.model.ModelPackage;
import org.ballerinalang.model.BallerinaFile;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.tree.BLangNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is the data model, which holds the data items required for filtering the suggestions
 */
public class SuggestionsFilterDataModel {

    private ParserRuleContext parserRuleContext;
    private List<PossibleToken> possibleTokens;
    private TokenStream tokenStream;
    private Vocabulary vocabulary;
    private int tokenIndex;
    private BallerinaFile ballerinaFile;
    private BLangNode symbolEnvNode;
    private Set<Map.Entry<String, ModelPackage>> packages;
    private SymbolTable symbolTable;

    /**
     * Constructor for SuggestionsFilterDataModel
     */
    public SuggestionsFilterDataModel(){
    }

    private void init(Parser parser) {
        if (parser != null) {
            this.tokenStream = parser.getTokenStream();
            this.vocabulary = parser.getVocabulary();
            this.tokenIndex = parser.getCurrentToken().getTokenIndex();
        }
    }

    public void initParserContext (Parser parser, ParserRuleContext parserRuleContext,
                                   List<PossibleToken> possibleTokenList) {
        this.parserRuleContext = parserRuleContext;
        this.possibleTokens = possibleTokenList;
        if (parser != null) {
            this.tokenStream = parser.getTokenStream();
            this.vocabulary = parser.getVocabulary();
            this.tokenIndex = parser.getCurrentToken().getTokenIndex();
        }
    }

    /**
     * Get the Context
     * @return {@link ParserRuleContext} parserRuleContext instance
     */
    public ParserRuleContext getParserRuleContext() {
        return parserRuleContext;
    }

    /**
     * Get the possible tokens list
     * @return {@link List}
     */
    public List<PossibleToken> getPossibleTokens() {
        return possibleTokens;
    }

    /**
     * Set the possible token list
     * @param possibleTokens - possible tokens
     */
    public void setPossibleTokens(List<PossibleToken> possibleTokens) {
        this.possibleTokens = possibleTokens;
    }

    /**
     * Get the token stream
     * @return {@link TokenStream}
     */
    public TokenStream getTokenStream() {
        return this.tokenStream;
    }

    /**
     * Get the vocabulary
     * @return {@link Vocabulary}
     */
    public Vocabulary getVocabulary() {
        return this.vocabulary;
    }

    /**
     * Get the token index
     * @return {@link int}
     */
    public int getTokenIndex() {
        return tokenIndex;
    }

    public BallerinaFile getBallerinaFile() {
        return ballerinaFile;
    }

    public void setBallerinaFile(BallerinaFile ballerinaFile) {
        this.ballerinaFile = ballerinaFile;
    }

    public Set<Map.Entry<String, ModelPackage>> getPackages() {
        return packages;
    }

    public void setPackages(Set<Map.Entry<String, ModelPackage>> packages) {
        this.packages = packages;
    }

    public BLangNode getSymbolEnvNode() {
        return symbolEnvNode;
    }

    public void setSymbolEnvNode(BLangNode symbolEnvNode) {
        this.symbolEnvNode = symbolEnvNode;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    public void setSymbolTable(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }
}
