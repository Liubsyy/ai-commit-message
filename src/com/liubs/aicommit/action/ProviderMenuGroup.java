package com.liubs.aicommit.action;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.liubs.aicommit.ai.ManagedFreeClient;
import com.liubs.aicommit.settings.AiCommitSettings;
import com.liubs.aicommit.settings.ProviderProfile;
import com.liubs.aicommit.util.Notifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 下拉菜单根:一级为供应商配置(当前生效的带勾号图标),
 * 二级子菜单列出该配置的 model,底部为「设置模型…」。
 */
public class ProviderMenuGroup extends ActionGroup implements DumbAware {

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        AiCommitSettings settings = AiCommitSettings.getInstance();
        ProviderProfile selected = settings.getSelectedProfile();
        List<AnAction> items = new ArrayList<>();

        for (ProviderProfile profile : settings.getState().profiles) {
            ActionGroup group;
            if (profile.isManagedFree()) {
                group = new ManagedFreeMenuGroup(profile.id, profile.name);
            } else {
                DefaultActionGroup staticGroup = new DefaultActionGroup(profile.name, true);
                addModelActions(staticGroup, profile);
                group = staticGroup;
            }
            if (selected != null && profile.id.equals(selected.id)) {
                group.getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
            }
            items.add(group);
        }

        if (items.isEmpty()) {
            items.add(disabledItem("(no model configured)"));
        }
        items.add(Separator.getInstance());
        items.add(new OpenSettingsAction());
        return items.toArray(AnAction.EMPTY_ARRAY);
    }

    private static void addModelActions(DefaultActionGroup group, ProviderProfile profile) {
        if (profile.models.isEmpty()) {
            group.add(disabledItem("(no models; fetch them in settings)"));
            return;
        }
        for (String model : profile.models) {
            group.add(new SelectModelAction(profile.id, model));
        }
    }

    private static AnAction[] modelActions(ProviderProfile profile) {
        if (profile.models.isEmpty()) {
            return new AnAction[]{disabledItem("(no free models available)")};
        }
        List<AnAction> actions = new ArrayList<>();
        for (String model : profile.models) {
            actions.add(new SelectModelAction(profile.id, model));
        }
        return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    private static final class ManagedFreeMenuGroup extends ActionGroup implements DumbAware {

        private static final long REFRESH_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);
        private static final AtomicBoolean REFRESH_IN_PROGRESS = new AtomicBoolean();
        private static final AtomicLong LAST_REFRESH_ATTEMPT_NANOS = new AtomicLong();

        private final String profileId;
        private boolean refreshAttempted;

        private ManagedFreeMenuGroup(String profileId, String name) {
            super(name, true);
            this.profileId = profileId;
        }

        @NotNull
        @Override
        public AnAction[] getChildren(@Nullable AnActionEvent e) {
            AiCommitSettings settings = AiCommitSettings.getInstance();
            ProviderProfile profile = settings.findProfile(profileId);
            if (profile == null) {
                return AnAction.EMPTY_ARRAY;
            }
            if (!refreshAttempted) {
                refreshAttempted = true;
                refreshModelsInBackground(profile, e);
            }
            return modelActions(profile);
        }

        private static void refreshModelsInBackground(ProviderProfile profile,
                                                      @Nullable AnActionEvent e) {
            long now = System.nanoTime();
            long lastAttempt = LAST_REFRESH_ATTEMPT_NANOS.get();
            if (lastAttempt != 0L && now - lastAttempt < REFRESH_INTERVAL_NANOS) {
                return;
            }
            if (!REFRESH_IN_PROGRESS.compareAndSet(false, true)) {
                return;
            }
            now = System.nanoTime();
            lastAttempt = LAST_REFRESH_ATTEMPT_NANOS.get();
            if (lastAttempt != 0L && now - lastAttempt < REFRESH_INTERVAL_NANOS) {
                REFRESH_IN_PROGRESS.set(false);
                return;
            }
            LAST_REFRESH_ATTEMPT_NANOS.set(now);
            Project project = e == null ? null : e.getProject();
            String baseUrl = profile.baseUrl;
            String id = profile.id;
            new Task.Backgroundable(project, "Refreshing Free Models", true) {
                private List<String> models;
                private IOException refreshError;

                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        models = new ManagedFreeClient().listModels(baseUrl, "", indicator);
                    } catch (IOException ex) {
                        refreshError = ex;
                    }
                }

                @Override
                public void onSuccess() {
                    if (models == null) {
                        if (refreshError != null) {
                            Notifier.warn(project,
                                    "Unable to refresh free models: " + refreshError.getMessage());
                        }
                        return;
                    }
                    ProviderProfile current = AiCommitSettings.getInstance().findProfile(id);
                    if (current == null || models.equals(current.models)) {
                        return;
                    }
                    current.models = new ArrayList<>(models);
                    if (!models.contains(current.selectedModel)) {
                        current.selectedModel = models.get(0);
                    }
                    ActivityTracker.getInstance().inc();
                }

                @Override
                public void onFinished() {
                    REFRESH_IN_PROGRESS.set(false);
                }
            }.queue();
        }
    }

    private static AnAction disabledItem(String text) {
        return new DumbAwareAction(text) {
            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(false);
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
            }
        };
    }
}
