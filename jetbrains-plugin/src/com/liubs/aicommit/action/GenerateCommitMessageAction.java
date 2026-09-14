package com.liubs.aicommit.action;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
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
import com.liubs.aicommit.util.ProgressTasks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 按钮主体:读取勾选变更 → 生成 diff → 调用 AI → 回填提交信息 */
public final class GenerateCommitMessageAction {

    /** completion 在整个流程结束时(含提前返回与后台任务完成)于 EDT 调用一次 */
    public void perform(@NotNull DataContext dataContext, @Nullable Runnable completion) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        CommitMessageI commitUi = VcsDataKeys.COMMIT_MESSAGE_CONTROL.getData(dataContext);
        if (commitUi == null) {
            Refreshable panel = Refreshable.PANEL_KEY.getData(dataContext);
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

        List<Change> changes = resolveChanges(dataContext);
        if (changes.isEmpty()) {
            Messages.showWarningDialog(project,
                    "No changes selected. Check the files to commit first.", "AI Commit Message");
            finish(completion);
            return;
        }

        ProviderProfile finalProfile = profile.copy();
        CommitMessageI finalCommitUi = commitUi;
        String apiKey = ApiKeyStore.get(profile.id);
        int charLimit = settings.getState().diffCharLimit;

        ProgressTasks.background(project, "Generating Commit Message with AI",
            indicator -> {
                indicator.setIndeterminate(true);
                indicator.setText("Collecting diff of selected changes…");
                String diff = ChangesDiffBuilder.buildDiff(project, changes, charLimit);
                indicator.checkCanceled();
                indicator.setText("Requesting " + finalProfile.name + " · " + finalProfile.selectedModel + "…");
                return AiClients.create(finalProfile)
                        .generateCommitMessage(finalProfile, apiKey, diff, indicator);
            },
            message -> {
                if (project.isDisposed()) {
                    return;
                }
                if (isBlank(message)) {
                    Notifier.warn(project, "The model returned empty content.");
                    return;
                }
                finalCommitUi.setCommitMessage(message);
            },
            ex -> Notifier.error(project, "Generation failed: " + ex.getMessage()),
            () -> finish(completion));
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
    private static List<Change> resolveChanges(DataContext dataContext) {
        CommitWorkflowHandler handler = VcsDataKeys.COMMIT_WORKFLOW_HANDLER.getData(dataContext);
        if (handler instanceof AbstractCommitWorkflowHandler) {
            return new ArrayList<>(((AbstractCommitWorkflowHandler<?, ?>) handler)
                    .getUi().getIncludedChanges());
        }
        Refreshable panel = Refreshable.PANEL_KEY.getData(dataContext);
        if (panel instanceof CheckinProjectPanel) {
            return new ArrayList<>(((CheckinProjectPanel) panel).getSelectedChanges());
        }
        return new ArrayList<>();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
