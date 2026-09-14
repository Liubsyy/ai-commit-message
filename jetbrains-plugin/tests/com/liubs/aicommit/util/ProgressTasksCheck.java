package com.liubs.aicommit.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone contract check; replaces task scheduling, not the SDK Task/WithResult classes. */
public final class ProgressTasksCheck {
    private static final class ApplicationBridge extends ApplicationManager {
        private static void install(Application application) {
            ourApplication = application;
        }
    }

    private static final class TestProgressManager extends CoreProgressManager {
        private boolean cancelBefore;
        private boolean cancelBeforeSuccess;
        private RuntimeException queueFailure;

        @Override
        public void run(Task task) {
            check(SwingUtilities.isEventDispatchThread(), "queue must start on EDT");
            if (queueFailure != null) {
                throw queueFailure;
            }
            boolean[] canceled = {cancelBefore};
            ProgressIndicator indicator = (ProgressIndicator) Proxy.newProxyInstance(
                    ProgressIndicator.class.getClassLoader(), new Class<?>[]{ProgressIndicator.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "cancel": canceled[0] = true; return null;
                            case "isCanceled": return canceled[0];
                            case "checkCanceled":
                                if (canceled[0]) throw new ProcessCanceledException();
                                return null;
                            default: return defaultValue(method.getReturnType());
                        }
                    });
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    task.run(indicator);
                } catch (Throwable failure) {
                    thrown.set(failure);
                }
            }, "progress-check-worker");
            worker.start();
            try {
                worker.join();
            } catch (InterruptedException interrupted) {
                throw new AssertionError(interrupted);
            }
            try {
                Throwable failure = thrown.get();
                if (canceled[0] || failure instanceof ProcessCanceledException) {
                    task.onCancel();
                } else if (failure != null) {
                    task.onThrowable(failure);
                } else {
                    if (cancelBeforeSuccess) canceled[0] = true;
                    task.onSuccess();
                }
            } finally {
                task.onFinished();
            }
        }

        private void reset() {
            cancelBefore = false;
            cancelBeforeSuccess = false;
            queueFailure = null;
        }
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectSame(Throwable expected, CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            check(actual == expected, "must propagate original " + expected.getClass().getSimpleName());
            return;
        }
        throw new AssertionError("expected " + expected.getClass().getSimpleName());
    }

    private static void expectCanceled(CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (ProcessCanceledException expected) {
            return;
        } catch (Exception failure) {
            throw new AssertionError("expected cancellation", failure);
        }
        throw new AssertionError("cancellation must not produce a result");
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        return null;
    }

    private static void verifyModal(TestProgressManager manager) throws Exception {
        check("result".equals(ProgressTasks.modal(null, "Check", indicator -> {
            check(!SwingUtilities.isEventDispatchThread(), "modal work must leave EDT");
            return "result";
        })), "modal result");
        IOException ioFailure = new IOException("network failed");
        expectSame(ioFailure, () -> ProgressTasks.modal(null, "Check", indicator -> { throw ioFailure; }));
        IllegalStateException runtimeFailure = new IllegalStateException("invalid state");
        expectSame(runtimeFailure, () -> ProgressTasks.modal(null, "Check", indicator -> { throw runtimeFailure; }));
        AssertionError fatalFailure = new AssertionError("fatal");
        expectSame(fatalFailure, () -> ProgressTasks.modal(null, "Check", indicator -> { throw fatalFailure; }));
        ProcessCanceledException cancellation = new ProcessCanceledException();
        expectSame(cancellation, () -> ProgressTasks.modal(null, "Check", indicator -> { throw cancellation; }));
        expectCanceled(() -> ProgressTasks.modal(null, "Check", indicator -> {
            indicator.cancel();
            return "stale result";
        }));
        manager.cancelBefore = true;
        expectCanceled(() -> ProgressTasks.modal(null, "Check", indicator -> {
            throw new AssertionError("work must not start after cancellation");
        }));
        manager.reset();
        manager.cancelBeforeSuccess = true;
        expectCanceled(() -> ProgressTasks.modal(null, "Check", indicator -> "stale result"));
        manager.reset();
    }

    private static void verifyBackground(TestProgressManager manager) {
        List<String> events = new ArrayList<>();
        Runnable finished = () -> {
            check(SwingUtilities.isEventDispatchThread(), "finished callback must run on EDT");
            events.add("finished");
        };
        java.util.function.Consumer<String> success = result -> {
            check(SwingUtilities.isEventDispatchThread(), "success callback must run on EDT");
            events.add(result);
        };
        java.util.function.Consumer<Exception> error = failure -> {
            check(SwingUtilities.isEventDispatchThread(), "error callback must run on EDT");
            events.add(failure.getMessage());
        };
        ProgressTasks.background(null, "Check", indicator -> {
            check(!SwingUtilities.isEventDispatchThread(), "background work must leave EDT");
            return "success";
        }, success, error, finished);
        check(events.equals(java.util.Arrays.asList("success", "finished")), "success then exactly one finish");
        events.clear();
        ProgressTasks.background(null, "Check", indicator -> { throw new IOException("error"); }, success, error, finished);
        check(events.equals(java.util.Arrays.asList("error", "finished")), "error then exactly one finish");
        events.clear();
        IllegalStateException operationFailure = new IllegalStateException("invalid operation");
        ProgressTasks.background(null, "Check", indicator -> { throw operationFailure; }, success,
                actual -> {
                    check(actual == operationFailure, "runtime failure must reach error callback unchanged");
                    error.accept(actual);
                }, finished);
        check(events.equals(java.util.Arrays.asList("invalid operation", "finished")), "runtime error then finish");
        events.clear();
        ProgressTasks.background(null, "Check", indicator -> { throw new ProcessCanceledException(); }, success, error, finished);
        check(events.equals(java.util.Collections.singletonList("finished")), "cancellation is not an error");
        events.clear();
        ProgressTasks.background(null, "Check", indicator -> {
            indicator.cancel();
            return "stale success";
        }, success, error, finished);
        check(events.equals(java.util.Collections.singletonList("finished")), "cancel after operation suppresses result");
        events.clear();
        manager.cancelBeforeSuccess = true;
        ProgressTasks.background(null, "Check", indicator -> "stale success", success, error, finished);
        check(events.equals(java.util.Collections.singletonList("finished")), "late cancellation suppresses callback");
        manager.reset();
        events.clear();
        Project disposedProject = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
                new Class<?>[]{Project.class}, (proxy, method, arguments) ->
                        "isDisposed".equals(method.getName()) ? true : defaultValue(method.getReturnType()));
        ProgressTasks.background(disposedProject, "Check", indicator -> "stale success", success, error, finished);
        check(events.equals(java.util.Collections.singletonList("finished")), "disposed project suppresses success");
        events.clear();
        ProgressTasks.background(disposedProject, "Check", indicator -> { throw new IOException("stale error"); },
                success, error, finished);
        check(events.equals(java.util.Collections.singletonList("finished")), "disposed project suppresses error");
        events.clear();
        RuntimeException callbackFailure = new IllegalArgumentException("callback failed");
        expectSame(callbackFailure, () -> ProgressTasks.background(null, "Check", indicator -> "success",
                result -> { throw callbackFailure; }, error, finished));
        check(events.equals(java.util.Collections.singletonList("finished")), "throwing callback finishes exactly once");
        events.clear();
        manager.queueFailure = new IllegalStateException("queue failed");
        expectSame(manager.queueFailure, () -> ProgressTasks.background(null, "Check", indicator -> "success", success, error, finished));
        check(events.equals(java.util.Collections.singletonList("finished")), "queue failure finishes exactly once");
        RuntimeException completionFailure = new IllegalArgumentException("completion failed");
        expectSame(manager.queueFailure, () -> ProgressTasks.background(null, "Check", indicator -> "success",
                success, error, () -> { throw completionFailure; }));
        check(manager.queueFailure.getSuppressed().length == 1
                && manager.queueFailure.getSuppressed()[0] == completionFailure,
                "completion failure must not replace queue failure");
        manager.reset();
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<ProgressManager> progressManager = new AtomicReference<>();
        Application application = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, arguments) -> {
                    if ("getService".equals(method.getName()) && arguments[0] == ProgressManager.class) {
                        return progressManager.get();
                    }
                    return defaultValue(method.getReturnType());
                });
        ApplicationBridge.install(application);
        TestProgressManager manager = new TestProgressManager();
        progressManager.set(manager);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyModal(manager);
                verifyBackground(manager);
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        manager.dispose();
        if (failure.get() != null) throw new AssertionError("Progress task check failed", failure.get());
        System.out.println("PASS: progress task results, errors, cancellation, EDT callbacks and completion guarantees");
        System.exit(0);
    }
}
