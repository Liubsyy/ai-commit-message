package com.liubs.aicommit.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public final class Notifier {

    private static final String GROUP_ID = "AI Commit Message";

    private Notifier() {
    }

    public static void info(@Nullable Project project, String content) {
        notify(project, content, NotificationType.INFORMATION);
    }

    public static void warn(@Nullable Project project, String content) {
        notify(project, content, NotificationType.WARNING);
    }

    public static void error(@Nullable Project project, String content) {
        notify(project, content, NotificationType.ERROR);
    }

    private static void notify(@Nullable Project project, String content, NotificationType type) {
        Notifications.Bus.notify(new Notification(GROUP_ID, "AI Commit Message", content, type), project);
    }
}
