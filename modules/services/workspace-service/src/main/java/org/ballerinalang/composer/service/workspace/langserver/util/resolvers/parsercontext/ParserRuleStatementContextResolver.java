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

package org.ballerinalang.composer.service.workspace.langserver.util.resolvers.parsercontext;

import org.ballerinalang.composer.service.workspace.langserver.SymbolInfo;
import org.ballerinalang.composer.service.workspace.langserver.dto.CompletionItem;
import org.ballerinalang.composer.service.workspace.langserver.util.filters.PackageActionAndFunctionFilter;
import org.ballerinalang.composer.service.workspace.langserver.util.filters.StatementTemplateFilter;
import org.ballerinalang.composer.service.workspace.langserver.util.resolvers.AbstractItemResolver;
import org.ballerinalang.composer.service.workspace.langserver.util.resolvers.ItemResolverConstants;
import org.ballerinalang.composer.service.workspace.suggetions.SuggestionsFilterDataModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Parser rule based statement context resolver
 */
public class ParserRuleStatementContextResolver extends AbstractItemResolver {
    @Override
    public ArrayList<CompletionItem> resolveItems(SuggestionsFilterDataModel dataModel, ArrayList<SymbolInfo> symbols,
                                                  HashMap<Class, AbstractItemResolver> resolvers) {

        HashMap<String, Integer> prioritiesMap = new HashMap<>();
        ArrayList<CompletionItem> completionItems = new ArrayList<>();

        // Here we specifically need to check whether the statement is function invocation,
        // action invocation or worker invocation
        if (isActionOrFunctionInvocationStatement(dataModel)) {
            PackageActionAndFunctionFilter actionAndFunctionFilter = new PackageActionAndFunctionFilter();

            // Get the action and function list
            ArrayList<SymbolInfo> actionFunctionList = actionAndFunctionFilter
                    .getCompletionItems(actionAndFunctionFilter.filterItems(dataModel, symbols, null).get(0), dataModel);

            // Populate the completion items
            this.populateCompletionItemList(actionFunctionList, completionItems);

            // Set the sorting priorities
            prioritiesMap.put(ItemResolverConstants.FUNCTION_TYPE, ItemResolverConstants.PRIORITY_7);
            prioritiesMap.put(ItemResolverConstants.ACTION_TYPE, ItemResolverConstants.PRIORITY_6);
            this.assignItemPriorities(prioritiesMap, completionItems);

            return completionItems;
        } else {
            populateCompletionItemList(symbols, completionItems);
            StatementTemplateFilter statementTemplateFilter = new StatementTemplateFilter();
            // Add the statement templates
            completionItems.addAll(statementTemplateFilter.filterItems(dataModel, symbols, null));

            CompletionItem xmlns = new CompletionItem();
            xmlns.setLabel(ItemResolverConstants.XMLNS);
            xmlns.setInsertText(ItemResolverConstants.NAMESPACE_DECLARATION_TEMPLATE);
            xmlns.setDetail(ItemResolverConstants.SNIPPET_TYPE);
            xmlns.setSortText(ItemResolverConstants.PRIORITY_7);
            completionItems.add(xmlns);

            CompletionItem workerItem = new CompletionItem();
            workerItem.setLabel(ItemResolverConstants.WORKER);
            workerItem.setInsertText(ItemResolverConstants.WORKER_TEMPLATE);
            workerItem.setDetail(ItemResolverConstants.WORKER_TYPE);
            workerItem.setSortText(ItemResolverConstants.PRIORITY_6);
            completionItems.add(workerItem);

            CompletionItem xmlAttribute = new CompletionItem();
            xmlAttribute.setInsertText(ItemResolverConstants.XML_ATTRIBUTE_REFERENCE_TEMPLATE);
            xmlAttribute.setLabel("@");
            xmlAttribute.setDetail("xmlAttribute");
            xmlAttribute.setSortText(ItemResolverConstants.PRIORITY_7);
            completionItems.add(xmlAttribute);

            // Add the var keyword
            CompletionItem varKeyword = new CompletionItem();
            varKeyword.setInsertText("var ");
            varKeyword.setLabel("var");
            varKeyword.setDetail(ItemResolverConstants.KEYWORD_TYPE);
            varKeyword.setSortText(ItemResolverConstants.PRIORITY_6);
            completionItems.add(varKeyword);

            prioritiesMap.put(ItemResolverConstants.PACKAGE_TYPE, ItemResolverConstants.PRIORITY_6);
            prioritiesMap.put(ItemResolverConstants.STATEMENT_TYPE, ItemResolverConstants.PRIORITY_5);
            this.assignItemPriorities(prioritiesMap, completionItems);

            return completionItems;
        }
    }
}
