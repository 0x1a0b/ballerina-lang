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

package org.ballerinalang.composer.service.workspace.langserver.util.completion.resolvers.parsercontext;

import org.ballerinalang.composer.service.workspace.langserver.SymbolInfo;
import org.ballerinalang.composer.service.workspace.langserver.dto.CompletionItem;
import org.ballerinalang.composer.service.workspace.langserver.util.completion.filters.PackageActionAndFunctionFilter;
import org.ballerinalang.composer.service.workspace.langserver.util.completion.resolvers.AbstractItemResolver;
import org.ballerinalang.composer.service.workspace.langserver.util.completion.resolvers.ItemResolverConstants;
import org.ballerinalang.composer.service.workspace.suggetions.SuggestionsFilterDataModel;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BTypeSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parser rule based variable definition statement context resolver
 */
public class ParserRuleVariableDefinitionStatementContextResolver extends AbstractItemResolver {
    @Override
    public ArrayList<CompletionItem> resolveItems(SuggestionsFilterDataModel dataModel, ArrayList<SymbolInfo> symbols,
                                                  HashMap<Class, AbstractItemResolver> resolvers) {

        // Here we specifically need to check whether the statement is function invocation,
        // action invocation or worker invocation
        if (isActionOrFunctionInvocationStatement(dataModel)) {
            PackageActionAndFunctionFilter actionAndFunctionFilter = new PackageActionAndFunctionFilter();
            ArrayList<SymbolInfo> actionAndFunctions = new ArrayList<>();
            actionAndFunctions.addAll(actionAndFunctionFilter.filterItems(dataModel, symbols, null));
            ArrayList<CompletionItem> completionItems = new ArrayList<>();
            this.populateCompletionItemList(actionAndFunctions, completionItems);
            return completionItems;
        } else {
            // Add the create keyword
            CompletionItem createKeyword = new CompletionItem();
            createKeyword.setInsertText("create ");
            createKeyword.setLabel("create");
            createKeyword.setDetail(ItemResolverConstants.KEYWORD_TYPE);
            createKeyword.setSortText(ItemResolverConstants.PRIORITY_7);

            ArrayList<CompletionItem> completionItems = new ArrayList<>();
            List<SymbolInfo> filteredList = symbols.stream()
                    .filter(
                            symbolInfo -> !((symbolInfo.getScopeEntry().symbol instanceof BTypeSymbol)
                                    && !(symbolInfo.getScopeEntry().symbol instanceof BPackageSymbol))
                    )
                    .collect(Collectors.toList());
            populateCompletionItemList(filteredList, completionItems);
            completionItems.add(createKeyword);
            return completionItems;
        }
    }
}
