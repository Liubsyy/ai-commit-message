# Model selector regression check

This standalone Swing check requires JDK 11 and the IntelliJ IDEA 2020.3 SDK. It dispatches real mouse events to the native combo arrow and verifies that the custom popup handles them, including after theme refreshes and with a noneditable provider. No IDE window is opened.

Run from `jetbrains-plugin` in PowerShell, setting the two paths for your installation:

```powershell
$ideaSdk = 'D:/Soft/idea2020'
$jdk = 'D:/Soft/Java/jdk-11.0.1'
New-Item -ItemType Directory -Force out/model-selector-check | Out-Null
& "$jdk/bin/javac.exe" --release 11 -encoding UTF-8 -cp "$ideaSdk/lib/*" -sourcepath src -d out/model-selector-check src/com/liubs/aicommit/settings/ui/ModelSelector.java tests/com/liubs/aicommit/settings/ui/ModelSelectorArrowCheck.java
& "$jdk/bin/java.exe" '-Djava.awt.headless=true' -cp "out/model-selector-check;$ideaSdk/lib/*" com.liubs.aicommit.settings.ui.ModelSelectorArrowCheck light
& "$jdk/bin/java.exe" '-Djava.awt.headless=true' -cp "out/model-selector-check;$ideaSdk/lib/*" com.liubs.aicommit.settings.ui.ModelSelectorArrowCheck dark
```

The check fails against the old implementation because the native popup bypasses `setPopupVisible()` when the arrow is clicked.

## Progress task compatibility check

`ProgressTasksCheck` checks the progress helper's result, cancellation and callback contract against the real 2020.3 `Task` classes. A controlled test scheduler executes work on a worker thread and callbacks on Swing EDT; it does not open an IDE or verify progress dialog appearance.

It covers checked exceptions, runtime exceptions and errors from modal work; cancellation before/after work and immediately before success callbacks; and completion exactly once on success, failure, cancellation, callback failure or queue failure.

```powershell
New-Item -ItemType Directory -Force out/progress-tasks-check | Out-Null
& "$jdk/bin/javac.exe" --release 11 -encoding UTF-8 -cp "$ideaSdk/lib/*" -d out/progress-tasks-check src/com/liubs/aicommit/util/ProgressTasks.java tests/com/liubs/aicommit/util/ProgressTasksCheck.java
& "$jdk/bin/java.exe" '-Djava.awt.headless=true' -cp "out/progress-tasks-check;$ideaSdk/lib/*" com.liubs.aicommit.util.ProgressTasksCheck
```

`ProgressTasks` intentionally retains the platform-managed `Task` API in one compatibility boundary. The coroutine replacement targets IntelliJ 2024.1+, while this plugin still supports 2020.3. Callers receive explicit indicators and preserve native progress and cancellation UI.
