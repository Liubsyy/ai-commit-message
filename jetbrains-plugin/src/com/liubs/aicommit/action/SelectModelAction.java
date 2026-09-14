package com.liubs.aicommit.action;

import com.intellij.openapi.actionSystem.ToggleOptionAction;
import com.intellij.openapi.project.DumbAware;
import com.liubs.aicommit.settings.AiCommitSettings;
import com.liubs.aicommit.settings.ProviderProfile;

/** 二级菜单项:选中即切换「当前配置 + selectedModel」 */
public class SelectModelAction extends ToggleOptionAction implements DumbAware {

    public SelectModelAction(String profileId, String model) {
        super(new ModelOption(profileId, model));
        getTemplatePresentation().setText(model);
    }

    /** ToggleOptionAction supplies EDT updates on newer IDEs while remaining available in 2020.3. */
    private static final class ModelOption implements Option {
        private final String profileId;
        private final String model;

        private ModelOption(String profileId, String model) {
            this.profileId = profileId;
            this.model = model;
        }

        @Override
        public String getName() {
            return model;
        }

        @Override
        public boolean isSelected() {
            AiCommitSettings settings = AiCommitSettings.getInstance();
            ProviderProfile selected = settings.getSelectedProfile();
            return selected != null && selected.id.equals(profileId)
                    && model.equals(selected.selectedModel);
        }

        @Override
        public void setSelected(boolean state) {
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
}
