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

package org.ballerinalang.plugins.idea.runconfig.ui;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import org.ballerinalang.plugins.idea.runconfig.BallerinaRunUtil;
import org.ballerinalang.plugins.idea.runconfig.RunConfigurationKind;
import org.ballerinalang.plugins.idea.runconfig.application.BallerinaApplicationConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;

public class BallerinaApplicationSettingsEditor extends SettingsEditor<BallerinaApplicationConfiguration> {

    private JPanel myPanel;
    private LabeledComponent<JComboBox<RunConfigurationKind>> myRunKindComboBox;
    private LabeledComponent<TextFieldWithBrowseButton> myFileField;
    private LabeledComponent<EditorTextField> myPackageField;
    private LabeledComponent<RawCommandLineEditor> myParamsField;
    private LabeledComponent<TextFieldWithBrowseButton> myWorkingDirectoryField;
    private LabeledComponent<ModulesComboBox> myModulesComboBox;
    private LabeledComponent<JBCheckBox> myRemoteDebuggingEnabled;
    private JPanel myRemoteDebuggingPanel;
    private LabeledComponent<EditorTextField> myHost;
    private LabeledComponent<EditorTextField> myPort;
    private Project myProject;
    private EditorTextField myHostField;
    private EditorTextField myPortField;

    public BallerinaApplicationSettingsEditor(Project project) {
        myProject = project;
        installRunKindComboBox();
        BallerinaRunUtil.installBallerinaWithMainFileChooser(project, myFileField.getComponent());
    }

    @Override
    protected void resetEditorFrom(@NotNull BallerinaApplicationConfiguration configuration) {
        myFileField.getComponent().setText(configuration.getFilePath());

        myPackageField.getComponent().setText(configuration.getPackage());

        myRunKindComboBox.getComponent().setSelectedItem(configuration.getRunKind());

        myModulesComboBox.getComponent().setModules(configuration.getValidModules());
        myModulesComboBox.getComponent().setSelectedModule(configuration.getConfigurationModule().getModule());

        myRemoteDebuggingEnabled.getComponent().setSelected(configuration.isRemoteDebuggingEnabled());
        onRemoteDebuggingStateChanged();

        myHost.getComponent().setText(configuration.getRemoteDebugHost());
        myPort.getComponent().setText(configuration.getRemoteDebugPort());

        myParamsField.getComponent().setText(configuration.getParams());
        myWorkingDirectoryField.getComponent().setText(configuration.getWorkingDirectory());
    }

    @Override
    protected void applyEditorTo(@NotNull BallerinaApplicationConfiguration configuration)
            throws ConfigurationException {
        configuration.setPackage(myPackageField.getComponent().getText());
        RunConfigurationKind runKind = (RunConfigurationKind) myRunKindComboBox.getComponent().getSelectedItem();
        configuration.setRunKind(runKind);
        configuration.setFilePath(myFileField.getComponent().getText());
        configuration.setModule(myModulesComboBox.getComponent().getSelectedModule());
        configuration.setParams(myParamsField.getComponent().getText());
        configuration.setWorkingDirectory(myWorkingDirectoryField.getComponent().getText());
        if (runKind == RunConfigurationKind.SERVICE) {
            myParamsField.setVisible(false);
        } else {
            myParamsField.setVisible(true);
        }
        configuration.setRemoteDebuggingEnabled(myRemoteDebuggingEnabled.getComponent().isSelected());
        configuration.setRemoteDebugHost(myHost.getComponent().getText());
        configuration.setRemoteDebugPort(myPort.getComponent().getText());
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myPanel;
    }

    private void createUIComponents() {
        myRunKindComboBox = new LabeledComponent<>();
        myRunKindComboBox.setComponent(new JComboBox<>());

        myFileField = new LabeledComponent<>();
        myFileField.setComponent(new TextFieldWithBrowseButton());

        myPackageField = new LabeledComponent<>();
        myPackageField.setComponent(new BallerinaPackageFieldCompletionProvider(
                () -> myModulesComboBox.getComponent().getSelectedModule()).createEditor(myProject));

        myWorkingDirectoryField = new LabeledComponent<>();
        myWorkingDirectoryField.setComponent(new TextFieldWithBrowseButton());

        myParamsField = new LabeledComponent<>();
        myParamsField.setComponent(new RawCommandLineEditor());

        myModulesComboBox = new LabeledComponent<>();
        myModulesComboBox.setComponent(new ModulesComboBox());

        myRemoteDebuggingEnabled = new LabeledComponent<>();
        JBCheckBox checkBox = new JBCheckBox();
        checkBox.addActionListener(event -> onRemoteDebuggingStateChanged());
        myRemoteDebuggingEnabled.setComponent(checkBox);

        myHost = new LabeledComponent<>();
        myHostField = new EditorTextField();
        myHostField.setPreferredWidth(300);
        myHost.setComponent(myHostField);

        myPort = new LabeledComponent<>();
        myPortField = new EditorTextField();
        myPortField.setPreferredWidth(100);
        myPort.setComponent(myPortField);
    }

    private static ListCellRendererWrapper<RunConfigurationKind> getRunKindListCellRendererWrapper() {
        return new ListCellRendererWrapper<RunConfigurationKind>() {
            @Override
            public void customize(JList list, @Nullable RunConfigurationKind kind, int index,
                                  boolean selected, boolean hasFocus) {
                if (kind != null) {
                    String kindName = StringUtil.capitalize(kind.toString().toLowerCase(Locale.US));
                    setText(kindName);
                }
            }
        };
    }

    private void installRunKindComboBox() {
        myRunKindComboBox.getComponent().removeAllItems();
        myRunKindComboBox.getComponent().setRenderer(getRunKindListCellRendererWrapper());
        for (RunConfigurationKind kind : RunConfigurationKind.values()) {
            myRunKindComboBox.getComponent().addItem(kind);
        }
        myRunKindComboBox.getComponent().addActionListener(e -> onRunKindChanged());
    }

    private void onRunKindChanged() {
        RunConfigurationKind selectedKind = (RunConfigurationKind) myRunKindComboBox.getComponent().getSelectedItem();
        if (selectedKind == null) {
            selectedKind = RunConfigurationKind.MAIN;
        }
        boolean isMainSelected = selectedKind == RunConfigurationKind.MAIN;
        myParamsField.setVisible(isMainSelected);
    }

    private void onRemoteDebuggingStateChanged() {
        boolean selected = myRemoteDebuggingEnabled.getComponent().isSelected();
        myHost.setEnabled(selected);
        myPort.setEnabled(selected);
    }
}
