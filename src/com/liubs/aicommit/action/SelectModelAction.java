package com.liubs.aicommit.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.liubs.aicommit.settings.AiCommitSettings;
import com.liubs.aicommit.settings.ProviderProfile;
import org.jetbrains.annotations.NotNull;

/** 二级菜单项:选中即切换「当前配置 + selectedModel」 */
public class SelectModelAction extends ToggleAction implements DumbAware {

    private final String profileId;
    private final String model;

    public SelectModelAction(String profileId, String model) {
        super(model);
        this.profileId = profileId;
        this.model = model;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        AiCommitSettings settings = AiCommitSettings.getInstance();
        ProviderProfile selected = settings.getSelectedProfile();
        return selected != null && selected.id.equals(profileId)
                && model.equals(selected.selectedModel);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (!state) {
            return;
        }
        AiCommitSettings settings = AiCommitSettings.getInstance();
        ProviderProfile profile = settings.findProfile(profileId);
        if (profile == null) {
            return;
        }
        settings.getState().selectedProfileId = profileId;
        profile.selectedModel = model;
    }
}
