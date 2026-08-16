package com.liubs.aicommit.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.liubs.aicommit.settings.ui.SettingsDialog;
import org.jetbrains.annotations.NotNull;

public class OpenSettingsAction extends DumbAwareAction {

    public OpenSettingsAction() {
        super("Model Settings…", "Add, remove, or edit AI model profiles", AllIcons.General.GearPlain);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        new SettingsDialog(e.getProject()).show();
    }
}
