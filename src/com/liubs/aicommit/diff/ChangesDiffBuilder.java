package com.liubs.aicommit.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;

import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

/** 把勾选的 Change 列表转成统一 diff 文本,超长按字符预算截断 */
public final class ChangesDiffBuilder {

    private ChangesDiffBuilder() {
    }

    public static String buildDiff(Project project, Collection<Change> changes, int charLimit) {
        String diff;
        try {
            String basePath = project.getBasePath() == null ? "" : project.getBasePath();
            List<FilePatch> patches = ApplicationManager.getApplication().runReadAction(
                    (ThrowableComputable<List<FilePatch>, Exception>) () ->
                            IdeaTextPatchBuilder.buildPatch(project, changes, basePath, false));
            StringWriter writer = new StringWriter();
            UnifiedDiffWriter.write(project, patches, writer, "\n", null);
            diff = writer.toString();
        } catch (Exception e) {
            diff = describeChanges(changes);
        }
        if (diff.trim().isEmpty()) {
            diff = describeChanges(changes);
        }
        if (charLimit > 0 && diff.length() > charLimit) {
            diff = diff.substring(0, charLimit)
                    + "\n\n[Note: diff truncated to the first " + charLimit + " characters]";
        }
        return diff.trim();
    }

    /** patch 生成失败时的兜底:至少给出文件级变更概览 */
    private static String describeChanges(Collection<Change> changes) {
        StringBuilder sb = new StringBuilder("Changed files (full diff unavailable):\n");
        for (Change change : changes) {
            sb.append("- ").append(change.getType()).append(' ')
                    .append(ChangesUtil.getFilePath(change).getPath()).append('\n');
        }
        return sb.toString();
    }
}
