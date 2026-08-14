package com.liubs.aicommit.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;
import com.intellij.vcs.commit.CommitWorkflowHandler;
import com.liubs.aicommit.ai.AiClients;
import com.liubs.aicommit.diff.ChangesDiffBuilder;
import com.liubs.aicommit.settings.AiCommitSettings;
import com.liubs.aicommit.settings.ApiKeyStore;
import com.liubs.aicommit.settings.ProviderProfile;
import com.liubs.aicommit.settings.ui.SettingsDialog;
import com.liubs.aicommit.util.Notifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 按钮主体:读取勾选变更 → 生成 diff → 调用 AI → 回填提交信息 */
public class GenerateCommitMessageAction extends DumbAwareAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabled = e.getProject() != null
                && e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        perform(e, null);
    }

    /** completion 在整个流程结束时(含提前返回与后台任务完成)于 EDT 调用一次 */
    public void perform(@NotNull AnActionEvent e, @Nullable Runnable completion) {
        Project project = e.getProject();
        CommitMessageI commitUi = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
        if (commitUi == null) {
            Refreshable panel = Refreshable.PANEL_KEY.getData(e.getDataContext());
            if (panel instanceof CommitMessageI) {
                commitUi = (CommitMessageI) panel;
            }
        }
        if (project == null || commitUi == null) {
            Messages.showErrorDialog(project,
                    "Cannot locate the commit message field. Use this button in the Commit panel.",
                    "AI Commit Message");
            finish(completion);
            return;
        }

        AiCommitSettings settings = AiCommitSettings.getInstance();
        ProviderProfile profile = settings.getSelectedProfile();
        if (profile == null || isBlank(profile.selectedModel)) {
            new SettingsDialog(project).show();
            profile = settings.getSelectedProfile();
            if (profile == null || isBlank(profile.selectedModel)) {
                Notifier.warn(project, "No available model configured. Generation canceled.");
                finish(completion);
                return;
            }
        }

        List<Change> changes = resolveChanges(e);
        if (changes.isEmpty()) {
            Messages.showWarningDialog(project,
                    "No changes selected. Check the files to commit first.", "AI Commit Message");
            finish(completion);
            return;
        }

        ProviderProfile finalProfile = profile;
        CommitMessageI finalCommitUi = commitUi;
        String apiKey = ApiKeyStore.get(profile.id);
        int charLimit = settings.getState().diffCharLimit;

        new Task.Backgroundable(project, "Generating Commit Message with AI", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Collecting diff of selected changes…");
                String diff = ChangesDiffBuilder.buildDiff(project, changes, charLimit);
                indicator.setText("Requesting " + finalProfile.name + " · " + finalProfile.selectedModel + "…");
                String message;
                try {
                    message = AiClients.create(finalProfile)
                            .generateCommitMessage(finalProfile, apiKey, diff, indicator);
                } catch (IOException ex) {
                    Notifier.error(project, "Generation failed: " + ex.getMessage());
                    return;
                }
                if (isBlank(message)) {
                    Notifier.warn(project, "The model returned empty content.");
                    return;
                }
                String finalMessage = message;
                ApplicationManager.getApplication().invokeLater(
                        () -> finalCommitUi.setCommitMessage(finalMessage), ModalityState.any());
            }

            @Override
            public void onFinished() {
                finish(completion);
            }
        }.queue();
    }

    private static void finish(@Nullable Runnable completion) {
        if (completion != null) {
            completion.run();
        }
    }

    /**
     * 只取用户勾选的变更:非模态 Commit 工具窗口走 workflow handler 的 includedChanges,
     * 旧版对话框走 CheckinProjectPanel 的 selectedChanges;都拿不到就返回空,由调用方提示。
     */
    private static List<Change> resolveChanges(AnActionEvent e) {
        CommitWorkflowHandler handler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER);
        if (handler instanceof AbstractCommitWorkflowHandler) {
            return new ArrayList<>(((AbstractCommitWorkflowHandler<?, ?>) handler)
                    .getUi().getIncludedChanges());
        }
        Refreshable panel = Refreshable.PANEL_KEY.getData(e.getDataContext());
        if (panel instanceof CheckinProjectPanel) {
            return new ArrayList<>(((CheckinProjectPanel) panel).getSelectedChanges());
        }
        return new ArrayList<>();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
