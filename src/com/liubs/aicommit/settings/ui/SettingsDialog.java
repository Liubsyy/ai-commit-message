package com.liubs.aicommit.settings.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.liubs.aicommit.ai.AiClient;
import com.liubs.aicommit.ai.AiClients;
import com.liubs.aicommit.ai.PromptTemplates;
import com.liubs.aicommit.settings.AiCommitSettings;
import com.liubs.aicommit.settings.ApiKeyStore;
import com.liubs.aicommit.settings.OutputLanguages;
import com.liubs.aicommit.settings.ProviderProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 模型设置对话框:左侧配置列表(增/删/复制),右侧表单 + 拉取模型 + 测试连接 */
public class SettingsDialog extends DialogWrapper {

    private final @Nullable Project project;

    private final DefaultListModel<ProfileEntry> listModel = new DefaultListModel<>();
    private final JBList<ProfileEntry> profileList = new JBList<>(listModel);

    private final JBTextField nameField = new JBTextField();
    private final JBTextField baseUrlField = new JBTextField();
    private final JBPasswordField apiKeyField = new JBPasswordField();
    private final ComboBox<String> modelCombo = new ComboBox<>();
    private final ComboBox<String> languageCombo = new ComboBox<>(OutputLanguages.LABELS);
    private final JButton fetchModelsButton = new JButton("Fetch From Provider");
    private final JButton testButton = new JButton("Test Connection");
    private final JButton restoreDefaultPromptButton = new JButton("Restore Default Prompt");
    private final JBTextArea promptArea = new JBTextArea();

    private final Set<String> originalProfileIds = new HashSet<>();
    private int currentIndex = -1;
    private JPanel rootPanel;

    private static final class ProfileEntry {
        final ProviderProfile profile;
        String apiKey;

        ProfileEntry(ProviderProfile profile, String apiKey) {
            this.profile = profile;
            this.apiKey = apiKey;
        }

        @Override
        public String toString() {
            return profile.name.trim().isEmpty() ? "(unnamed)" : profile.name;
        }
    }

    public SettingsDialog(@Nullable Project project) {
        super(project, true);
        this.project = project;
        setTitle("AI Commit Model Settings");
        setOKButtonText("Save");
        setCancelButtonText("Cancel");

        AiCommitSettings settings = AiCommitSettings.getInstance();
        for (ProviderProfile p : settings.getState().profiles) {
            listModel.addElement(new ProfileEntry(p.copy(), ApiKeyStore.get(p.id)));
            originalProfileIds.add(p.id);
        }
        init();

        if (listModel.isEmpty()) {
            loadForm(-1);
        } else {
            int index = 0;
            ProviderProfile selected = settings.getSelectedProfile();
            if (selected != null) {
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).profile.id.equals(selected.id)) {
                        index = i;
                        break;
                    }
                }
            }
            profileList.setSelectedIndex(index);
        }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setFixedCellHeight(JBUI.scale(26));
        profileList.getEmptyText().setText("Click + to add a profile");
        profileList.setCellRenderer(new ColoredListCellRenderer<ProfileEntry>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends ProfileEntry> list,
                                                 ProfileEntry value, int index,
                                                 boolean selected, boolean hasFocus) {
                ProviderProfile active = AiCommitSettings.getInstance().getSelectedProfile();
                boolean isActive = active != null && active.id.equals(value.profile.id);
                setIcon(isActive ? AllIcons.Actions.Checked : EmptyIcon.ICON_16);
                append(value.toString());
                if (!value.profile.selectedModel.isEmpty()) {
                    append("  " + value.profile.selectedModel,
                            SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                }
            }
        });
        profileList.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });

        JPanel listPanel = ToolbarDecorator.createDecorator(profileList)
                .setAddAction(button -> addProfile())
                .setRemoveAction(button -> removeProfile())
                .addExtraAction(new AnActionButton("Duplicate Profile", AllIcons.Actions.Copy) {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        duplicateProfile();
                    }
                })
                .disableUpDownActions()
                .createPanel();
        JPanel left = new JPanel(new BorderLayout());
        left.add(listPanel, BorderLayout.CENTER);

        modelCombo.setEditable(true);
        fetchModelsButton.setIcon(AllIcons.Actions.Download);
        JPanel modelRow = new JPanel(new BorderLayout(8, 0));
        modelRow.add(modelCombo, BorderLayout.CENTER);
        modelRow.add(fetchModelsButton, BorderLayout.EAST);
        fetchModelsButton.addActionListener(ev -> fetchModels());

        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setRows(10);
        JBScrollPane promptScroll = new JBScrollPane(promptArea);

        JBLabel promptHint = new JBLabel("The built-in Conventional Commits template is prefilled and editable");
        promptHint.setForeground(JBColor.GRAY);
        promptHint.setFont(JBUI.Fonts.smallFont());
        promptHint.setBorder(JBUI.Borders.emptyTop(2));

        testButton.setIcon(AllIcons.Actions.Execute);
        testButton.addActionListener(ev -> testConnection());
        restoreDefaultPromptButton.addActionListener(ev -> restoreDefaultPrompt());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.add(testButton);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(restoreDefaultPromptButton);
        buttonRow.setBorder(JBUI.Borders.emptyTop(8));

        JPanel form = FormBuilder.createFormBuilder()
                .setVerticalGap(10)
                .addLabeledComponent("Name:", nameField)
                .addLabeledComponent("Base URL:", baseUrlField)
                .addLabeledComponent("API Key:", apiKeyField)
                .addLabeledComponent("Model:", modelRow)
                .addLabeledComponent("Output language:", languageCombo)
                .addLabeledComponent("Prompt:", promptScroll, true)
                .addComponentToRightColumn(promptHint)
                .addComponentToRightColumn(buttonRow)
                .getPanel();

        JPanel right = new JPanel(new BorderLayout());
        right.add(form, BorderLayout.NORTH);
        right.setBorder(JBUI.Borders.empty(4, 16, 4, 4));

        // The form can be taller than the old fixed 460 px at larger IDE font
        // or UI scales. BorderLayout.NORTH keeps the child's preferred height,
        // so a shorter parent clips the last row instead of shrinking it.
        int contentHeight = Math.max(
                JBUI.scale(460),
                form.getPreferredSize().height + JBUI.scale(12));
        left.setPreferredSize(new Dimension(JBUI.scale(210), contentHeight));
        right.setPreferredSize(new Dimension(JBUI.scale(540), contentHeight));

        rootPanel = new JPanel(new BorderLayout());
        rootPanel.add(left, BorderLayout.WEST);
        rootPanel.add(right, BorderLayout.CENTER);
        rootPanel.setBorder(JBUI.Borders.emptyTop(4));
        return rootPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return profileList;
    }

    private void onSelectionChanged() {
        int newIndex = profileList.getSelectedIndex();
        if (newIndex == currentIndex) {
            return;
        }
        saveForm(currentIndex);
        currentIndex = newIndex;
        loadForm(newIndex);
    }

    private void loadForm(int index) {
        boolean hasProfile = index >= 0 && index < listModel.size();
        if (!hasProfile) {
            nameField.setText("");
            baseUrlField.setText("");
            apiKeyField.setText("");
            modelCombo.setModel(new DefaultComboBoxModel<>());
            languageCombo.setSelectedIndex(0);
            promptArea.setText("");
        } else {
            ProfileEntry entry = listModel.get(index);
            ProviderProfile p = entry.profile;
            nameField.setText(p.name);
            baseUrlField.setText(p.baseUrl);
            apiKeyField.setText(entry.apiKey);
            modelCombo.setModel(new DefaultComboBoxModel<>(p.models.toArray(new String[0])));
            modelCombo.setSelectedItem(p.selectedModel);
            languageCombo.setSelectedIndex(OutputLanguages.indexOf(p.outputLanguage));
            promptArea.setText(p.prompt == null || p.prompt.trim().isEmpty()
                    ? PromptTemplates.getDefaultPrompt() : p.prompt);
            promptArea.setCaretPosition(0);
        }
        setFormEnabled(hasProfile);
        boolean managedFree = hasProfile && listModel.get(index).profile.isManagedFree();
        modelCombo.setEditable(hasProfile && !managedFree);
        if (managedFree) {
            nameField.setEnabled(false);
            baseUrlField.setEnabled(false);
            apiKeyField.setEnabled(false);
        }
    }

    private void saveForm(int index) {
        if (index < 0 || index >= listModel.size()) {
            return;
        }
        ProfileEntry entry = listModel.get(index);
        ProviderProfile p = entry.profile;
        if (!p.isManagedFree()) {
            p.name = nameField.getText().trim();
            p.baseUrl = baseUrlField.getText().trim();
            entry.apiKey = new String(apiKeyField.getPassword()).trim();
        }
        p.prompt = promptArea.getText();
        p.models = comboItems();
        p.selectedModel = currentComboText();
        if (!p.selectedModel.isEmpty() && !p.models.contains(p.selectedModel)) {
            p.models.add(p.selectedModel);
        }
        p.outputLanguage = OutputLanguages.codeAt(languageCombo.getSelectedIndex());
        listModel.set(index, entry);
    }

    private List<String> comboItems() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < modelCombo.getItemCount(); i++) {
            String item = modelCombo.getItemAt(i);
            if (item != null && !item.trim().isEmpty() && !items.contains(item.trim())) {
                items.add(item.trim());
            }
        }
        return items;
    }

    private String currentComboText() {
        Object item = modelCombo.isEditable()
                ? modelCombo.getEditor().getItem() : modelCombo.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    private void setFormEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        baseUrlField.setEnabled(enabled);
        apiKeyField.setEnabled(enabled);
        modelCombo.setEnabled(enabled);
        languageCombo.setEnabled(enabled);
        fetchModelsButton.setEnabled(enabled);
        testButton.setEnabled(enabled);
        restoreDefaultPromptButton.setEnabled(enabled);
        promptArea.setEnabled(enabled);
    }

    private void restoreDefaultPrompt() {
        promptArea.setText(PromptTemplates.getDefaultPrompt());
        promptArea.setCaretPosition(0);
        promptArea.requestFocusInWindow();
    }

    private void addProfile() {
        ProviderProfile p = new ProviderProfile();
        p.name = "New Profile";
        p.baseUrl = "https://api.openai.com/v1";
        p.prompt = PromptTemplates.getDefaultPrompt();
        listModel.addElement(new ProfileEntry(p, ""));
        profileList.setSelectedIndex(listModel.size() - 1);
    }

    private void removeProfile() {
        int index = profileList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        if (listModel.get(index).profile.isManagedFree()) {
            Messages.showInfoMessage(rootPanel,
                    "The built-in free provider cannot be removed.", "AI Commit Model Settings");
            return;
        }
        currentIndex = -1;
        listModel.remove(index);
        if (!listModel.isEmpty()) {
            profileList.setSelectedIndex(Math.min(index, listModel.size() - 1));
        } else {
            loadForm(-1);
        }
    }

    private void duplicateProfile() {
        int index = profileList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        saveForm(index);
        ProfileEntry source = listModel.get(index);
        if (source.profile.isManagedFree()) {
            Messages.showInfoMessage(rootPanel,
                    "The built-in free provider cannot be duplicated.", "AI Commit Model Settings");
            return;
        }
        ProviderProfile copy = source.profile.copy();
        copy.id = UUID.randomUUID().toString();
        copy.name = source.profile.name + " (copy)";
        listModel.addElement(new ProfileEntry(copy, source.apiKey));
        profileList.setSelectedIndex(listModel.size() - 1);
    }

    private void fetchModels() {
        int index = profileList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        saveForm(index);
        ProfileEntry entry = listModel.get(index);
        String baseUrl = entry.profile.baseUrl;
        if (baseUrl.isEmpty()) {
            Messages.showWarningDialog(rootPanel, "Fill in Base URL first", "Fetch Models");
            return;
        }
        String apiKey = entry.apiKey;
        try {
            AiClient client = AiClients.create(entry.profile);
            List<String> models = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    () -> client.listModels(baseUrl, apiKey,
                            ProgressManager.getInstance().getProgressIndicator()),
                    "Fetching Models From Provider", true, project);
            String current = currentComboText();
            DefaultComboBoxModel<String> comboModel =
                    new DefaultComboBoxModel<>(models.toArray(new String[0]));
            boolean managedFree = entry.profile.isManagedFree();
            if (!managedFree && !current.isEmpty() && comboModel.getIndexOf(current) < 0) {
                comboModel.addElement(current);
            }
            modelCombo.setModel(comboModel);
            if (!current.isEmpty() && comboModel.getIndexOf(current) >= 0) {
                modelCombo.setSelectedItem(current);
            } else if (managedFree) {
                modelCombo.setSelectedItem(null);
            } else {
                modelCombo.setSelectedItem(models.get(0));
            }
            Messages.showInfoMessage(rootPanel,
                    "Fetched " + models.size() + " models", "Fetch Models");
        } catch (ProcessCanceledException ignored) {
        } catch (Exception ex) {
            Messages.showErrorDialog(rootPanel, "Fetch failed: " + ex.getMessage(), "Fetch Models");
        }
    }

    private void testConnection() {
        int index = profileList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        saveForm(index);
        ProfileEntry entry = listModel.get(index);
        if (entry.profile.baseUrl.isEmpty()) {
            Messages.showWarningDialog(rootPanel, "Fill in Base URL first", "Test Connection");
            return;
        }
        try {
            String result = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                AiClient client = AiClients.create(entry.profile);
                if (entry.profile.selectedModel.isEmpty()) {
                    List<String> models = client.listModels(entry.profile.baseUrl, entry.apiKey,
                            ProgressManager.getInstance().getProgressIndicator());
                    return "Connection OK. Provider returned " + models.size() + " models.";
                }
                String reply = client.ping(entry.profile, entry.apiKey,
                        ProgressManager.getInstance().getProgressIndicator());
                return "Connection OK. Model replied: " + reply;
            }, "Testing Connection", true, project);
            Messages.showInfoMessage(rootPanel, result, "Test Connection");
        } catch (ProcessCanceledException ignored) {
        } catch (Exception ex) {
            Messages.showErrorDialog(rootPanel, "Connection failed: " + ex.getMessage(), "Test Connection");
        }
    }

    @Override
    protected void doOKAction() {
        saveForm(currentIndex);

        List<ProfileEntry> entries = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            entries.add(listModel.get(i));
        }
        for (int i = 0; i < entries.size(); i++) {
            ProviderProfile p = entries.get(i).profile;
            if (p.name.trim().isEmpty() || p.baseUrl.trim().isEmpty()) {
                profileList.setSelectedIndex(i);
                Messages.showErrorDialog(rootPanel,
                        "Name and Base URL must not be empty", "AI Commit Model Settings");
                return;
            }
        }

        AiCommitSettings settings = AiCommitSettings.getInstance();
        List<ProviderProfile> profiles = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        for (ProfileEntry entry : entries) {
            profiles.add(entry.profile);
            keptIds.add(entry.profile.id);
            ApiKeyStore.set(entry.profile.id, entry.apiKey);
        }
        for (String removedId : originalProfileIds) {
            if (!keptIds.contains(removedId)) {
                ApiKeyStore.set(removedId, null);
            }
        }
        settings.getState().profiles = profiles;
        if (settings.findProfile(settings.getState().selectedProfileId) == null) {
            settings.getState().selectedProfileId = profiles.isEmpty() ? "" : profiles.get(0).id;
        }
        super.doOKAction();
    }
}
