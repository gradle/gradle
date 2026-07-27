/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.SafeFileLocationUtils;

import java.util.List;
import java.util.Objects;

/**
 * Utilities related to generating file paths for the HTML test report.
 */
public class HtmlTestReportPathBuilder {

    static String buildFilePathForModel(boolean shrink, TestTreeModel tree) {
        return buildFilePath(shrink, tree.getPath(), tree.getChildren().isEmpty());
    }

    public static String buildFilePath(boolean shrink, org.gradle.util.Path path, boolean isLeaf) {
        List<SafeFileLocationUtils.Segment> segments = buildSegments(path, isLeaf);
        return shrink
            ? SafeFileLocationUtils.toMinimumSafeFilePath(segments)
            : SafeFileLocationUtils.toSafeFilePath(segments);
    }

    static boolean needsShrinking(SafeFileLocationUtils.PathLimitChecker pathLimitChecker, TestTreeModel tree) {
        if (tree.getChildren().isEmpty()) {
            // Only check leaves — a leaf path is always at least as long as its ancestors,
            // so if any path exceeds the limit, a leaf will too.
            List<SafeFileLocationUtils.Segment> segments = buildSegments(tree.getPath(), true);
            SafeFileLocationUtils.PathLimitCheckResult checkResult = pathLimitChecker.check(segments);
            if (checkResult == SafeFileLocationUtils.PathLimitCheckResult.UNSHRINKABLE) {
                throw new UnshrinkableReportPathException(SafeFileLocationUtils.toSafeFilePath(segments));
            }
            return checkResult == SafeFileLocationUtils.PathLimitCheckResult.EXCEEDS_LIMIT;
        }
        boolean needsShrinking = false;
        for (TestTreeModel child : tree.getChildren()) {
            // Cannot short-circuit here, even if one child needs shrinking,
            // we still need to check all children to validate that none are unshrinkable
            needsShrinking |= needsShrinking(pathLimitChecker, child);
        }
        return needsShrinking;
    }

    /**
     * Build the segments for a given path, used both for shrink-mode detection and file path generation.
     */
    private static List<SafeFileLocationUtils.Segment> buildSegments(org.gradle.util.Path path, boolean isLeaf) {
        if (path.segmentCount() == 0) {
            return ImmutableList.of(SafeFileLocationUtils.Segment.file("index.html"));
        }

        List<String> directoryNames;
        String fileName;
        if (isLeaf && !Objects.equals(path.getName(), "index")) {
            // Flat leaves use a/b/c.html instead of a/b/c/index.html,
            // reducing VFS overhead from many directories for large test suites
            directoryNames = path.getParent().segments();
            fileName = path.getName() + ".html";
        } else {
            directoryNames = path.segments();
            fileName = "index.html";
        }

        ImmutableList.Builder<SafeFileLocationUtils.Segment> builder = ImmutableList.builderWithExpectedSize(directoryNames.size() + 1);
        for (String dirName : directoryNames) {
            builder.add(SafeFileLocationUtils.Segment.directory(dirName));
        }
        builder.add(SafeFileLocationUtils.Segment.file(fileName));
        return builder.build();
    }
}
