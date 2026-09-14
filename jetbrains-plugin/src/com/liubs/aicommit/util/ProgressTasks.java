package com.liubs.aicommit.util;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Progress API compatibility boundary for the IntelliJ 2020.3 baseline.
 * Task is obsolete on newer IDEs, whose coroutine replacement requires 2024.1+.
 * Keep the platform's progress UI, cancellation and EDT callbacks here until
 * the minimum supported IDE can move to that API.
 */
public final class ProgressTasks {

    @FunctionalInterface
    public interface Operation<T> {
        T run(ProgressIndicator indicator) throws Exception;
    }

    private ProgressTasks() {
    }

    /** Runs with modal progress and returns only after completion; call from EDT. */
    public static <T> T modal(@Nullable Project project, String title,
                              Operation<T> operation) throws Exception {
        class ModalOperation extends Task.WithResult<T, Exception> {
            private boolean canceled;
            private ProgressIndicator indicator;

            private ModalOperation() {
                super(project, title, true);
            }

            @Override
            protected T compute(@NotNull ProgressIndicator progress) throws Exception {
                indicator = progress;
                progress.setIndeterminate(true);
                progress.checkCanceled();
                T result = operation.run(progress);
                progress.checkCanceled();
                return result;
            }

            @Override
            public void onCancel() {
                canceled = true;
            }

            private T result() throws Exception {
                if (canceled || indicator == null || indicator.isCanceled()) {
                    throw new ProcessCanceledException();
                }
                // WithResult preserves checked exceptions, runtime exceptions and Errors.
                return getResult();
            }
        }

        ModalOperation task = new ModalOperation();
        task.queue();
        return task.result();
    }

    /**
     * Runs work in the background. Task callbacks run on EDT. Cancellation skips
     * success/error; finished still runs exactly once. If queueing itself throws,
     * finished runs immediately on the caller's thread before propagating the failure.
     */
    public static <T> void background(@Nullable Project project, String title,
                                      Operation<T> operation, Consumer<T> success,
                                      Consumer<Exception> error, Runnable finished) {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finishOnce = () -> {
            if (completed.compareAndSet(false, true)) {
                finished.run();
            }
        };
        Task.Backgroundable task = new Task.Backgroundable(project, title, true) {
            private T result;
            private Exception failure;
            private ProgressIndicator indicator;

            @Override
            public void run(@NotNull ProgressIndicator progress) {
                indicator = progress;
                progress.setIndeterminate(true);
                progress.checkCanceled();
                try {
                    result = operation.run(progress);
                } catch (ProcessCanceledException canceled) {
                    throw canceled;
                } catch (Exception exception) {
                    failure = exception;
                }
                progress.checkCanceled();
            }

            @Override
            public void onSuccess() {
                // Cancellation can arrive after the platform chose the success callback.
                if (indicator == null || indicator.isCanceled()
                        || (project != null && project.isDisposed())) {
                    return;
                }
                if (failure != null) {
                    error.accept(failure);
                } else {
                    success.accept(result);
                }
            }

            @Override
            public void onFinished() {
                finishOnce.run();
            }
        };
        try {
            task.queue();
        } catch (RuntimeException | Error exception) {
            try {
                finishOnce.run();
            } catch (RuntimeException | Error completionFailure) {
                if (completionFailure != exception) {
                    exception.addSuppressed(completionFailure);
                }
            }
            throw exception;
        }
    }
}
