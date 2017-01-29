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

package org.ballerinalang.plugins.idea.run.configuration.application;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import org.ballerinalang.plugins.idea.BallerinaIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class BallerinaApplicationRunConfigurationType implements ConfigurationType {

    @Override
    public String getDisplayName() {
        return "Ballerina Application";
    }

    @Override
    public String getConfigurationTypeDescription() {
        return "Ballerina Application Run Configuration Type";
    }

    @Override
    public Icon getIcon() {
        return BallerinaIcons.FILE;
    }

    @NotNull
    @Override
    public String getId() {
        return "BALLERINA_APPLICATION_RUN_CONFIGURATION";
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{new BallerinaApplicationConfigurationFactory(this)};
    }
}
