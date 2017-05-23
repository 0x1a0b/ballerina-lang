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

package runconfig;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.ballerinalang.plugins.idea.run.configuration.RunConfigurationKind;
import org.ballerinalang.plugins.idea.run.configuration.application.BallerinaApplicationConfiguration;
import org.ballerinalang.plugins.idea.run.configuration.application.BallerinaApplicationRunConfigurationProducer;
import org.ballerinalang.plugins.idea.run.configuration.application.BallerinaApplicationRunConfigurationType;

public class BallerinaRunConfigurationProducerTest extends LightPlatformCodeInsightFixtureTestCase {

    @Override
    protected String getTestDataPath() {
        return "src/test/resources/testData/runconfig";
    }

    public void testMainWithoutPackageRunConfiguration() {
        PsiFile file = myFixture.configureByText("a.bal", "<caret>\nfunction main() {}");
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        BallerinaApplicationConfiguration runConfiguration =
                createRunMainPackageConfiguration(file.getVirtualFile().getPath());
        assertTrue(producer.isConfigurationFromContext(runConfiguration, configurationContext));

        runConfiguration = createRunMainPackageConfiguration(file.getVirtualFile().getPath() + "_vl");
        assertFalse(producer.isConfigurationFromContext(runConfiguration, configurationContext));
    }

    public void testServiceWithoutPackageRunConfiguration() {
        PsiFile file = myFixture.configureByText("a.bal", "<caret>\nservice main {}");
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        BallerinaApplicationConfiguration runConfiguration =
                createRunMainPackageConfiguration(file.getVirtualFile().getPath());
        assertTrue(producer.isConfigurationFromContext(runConfiguration, configurationContext));

        runConfiguration = createRunMainPackageConfiguration(file.getVirtualFile().getPath() + "_vl");
        assertFalse(producer.isConfigurationFromContext(runConfiguration, configurationContext));
    }

    public void testMainWithPackageRunConfiguration() {
        PsiFile file = myFixture.addFileToProject("org/test/a.bal", "package org.test; <caret>\nfunction main() {}");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        BallerinaApplicationConfiguration runConfiguration =
                createRunMainPackageConfiguration(file.getVirtualFile().getPath());
        assertTrue(producer.isConfigurationFromContext(runConfiguration, configurationContext));

        runConfiguration = createRunMainPackageConfiguration(file.getVirtualFile().getPath() + "_vl");
        assertFalse(producer.isConfigurationFromContext(runConfiguration, configurationContext));
    }

    public void testServiceWithPackageRunConfiguration() {
        PsiFile file = myFixture.addFileToProject("org/test/a.bal", "package org.test; <caret>\nservice main {}");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        BallerinaApplicationConfiguration runConfiguration =
                createRunMainPackageConfiguration(file.getVirtualFile().getPath());
        assertTrue(producer.isConfigurationFromContext(runConfiguration, configurationContext));

        runConfiguration = createRunMainPackageConfiguration(file.getVirtualFile().getPath() + "_vl");
        assertFalse(producer.isConfigurationFromContext(runConfiguration, configurationContext));
    }

    public void testMainWithoutPackageRunConfigurationFromContext() {
        PsiFile file = myFixture.configureByText("a.bal", "function <caret>main() {}");
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        ConfigurationFromContext configurationFromContext =
                producer.createConfigurationFromContext(configurationContext);
        assertNotNull(configurationFromContext);

        RunConfiguration runConfiguration = configurationFromContext.getConfiguration();
        assertTrue(runConfiguration instanceof BallerinaApplicationConfiguration);

        BallerinaApplicationConfiguration ballerinaApplicationConfiguration =
                (BallerinaApplicationConfiguration) runConfiguration;
        assertEquals(RunConfigurationKind.MAIN, ballerinaApplicationConfiguration.getRunKind());
        assertEquals(file.getVirtualFile().getPath(), ballerinaApplicationConfiguration.getFilePath());
        assertEmpty(ballerinaApplicationConfiguration.getPackage());
    }

    public void testServiceWithoutPackageRunConfigurationFromContext() {
        PsiFile file = myFixture.configureByText("a.bal", "service <caret>main {}");
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        ConfigurationFromContext configurationFromContext =
                producer.createConfigurationFromContext(configurationContext);
        assertNotNull(configurationFromContext);

        RunConfiguration runConfiguration = configurationFromContext.getConfiguration();
        assertTrue(runConfiguration instanceof BallerinaApplicationConfiguration);

        BallerinaApplicationConfiguration ballerinaApplicationConfiguration =
                (BallerinaApplicationConfiguration) runConfiguration;

        assertEquals(RunConfigurationKind.SERVICE, ballerinaApplicationConfiguration.getRunKind());
        assertEquals(file.getVirtualFile().getPath(), ballerinaApplicationConfiguration.getFilePath());
        assertEmpty(ballerinaApplicationConfiguration.getPackage());
    }

    public void testMainWithPackageRunConfigurationFromContext() {
        PsiFile file = myFixture.addFileToProject("org/test/a.bal", "package org.test;\nfunction <caret>main() {}");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        ConfigurationFromContext configurationFromContext =
                producer.createConfigurationFromContext(configurationContext);
        assertNotNull(configurationFromContext);

        RunConfiguration runConfiguration = configurationFromContext.getConfiguration();
        assertTrue(runConfiguration instanceof BallerinaApplicationConfiguration);

        BallerinaApplicationConfiguration ballerinaApplicationConfiguration =
                (BallerinaApplicationConfiguration) runConfiguration;
        assertEquals(RunConfigurationKind.MAIN, ballerinaApplicationConfiguration.getRunKind());
        assertEquals(file.getVirtualFile().getPath(), ballerinaApplicationConfiguration.getFilePath());
        assertEquals("org/test", ballerinaApplicationConfiguration.getPackage());
    }

    public void testServiceWithPackageRunConfigurationFromContext() {
        PsiFile file = myFixture.addFileToProject("org/test/a.bal", "package org.test;\nservice <caret>main {}");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiElement element = file.findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        ConfigurationContext configurationContext = new ConfigurationContext(element);
        BallerinaApplicationRunConfigurationProducer producer = new BallerinaApplicationRunConfigurationProducer();

        ConfigurationFromContext configurationFromContext =
                producer.createConfigurationFromContext(configurationContext);
        assertNotNull(configurationFromContext);

        RunConfiguration runConfiguration = configurationFromContext.getConfiguration();
        assertTrue(runConfiguration instanceof BallerinaApplicationConfiguration);

        BallerinaApplicationConfiguration ballerinaApplicationConfiguration =
                (BallerinaApplicationConfiguration) runConfiguration;
        assertEquals(RunConfigurationKind.SERVICE, ballerinaApplicationConfiguration.getRunKind());
        assertEquals(file.getVirtualFile().getPath(), ballerinaApplicationConfiguration.getFilePath());
        assertEquals("org/test", ballerinaApplicationConfiguration.getPackage());
    }

    private BallerinaApplicationConfiguration createRunMainPackageConfiguration(String filePath) {
        BallerinaApplicationRunConfigurationType type = BallerinaApplicationRunConfigurationType.getInstance();
        BallerinaApplicationConfiguration result = new BallerinaApplicationConfiguration(myFixture.getProject(),
                "Run Application", type);
        result.setFilePath(filePath);
        return result;
    }
}
