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

package org.gradle.problems.internal.services;

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.internal.DetectionContext;
import org.gradle.api.problems.internal.ProjectIdentity;
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.api.problems.internal.location.BuildFrame;
import org.gradle.api.problems.internal.location.Frame;
import org.gradle.api.problems.internal.location.Locality;
import org.gradle.api.problems.internal.location.PluginApplicationFrame;
import org.gradle.api.problems.internal.location.ProjectFrame;
import org.gradle.api.problems.internal.location.TaskFrame;
import org.gradle.api.problems.internal.location.Trace;

/**
 * Builds the detection trace of a problem: what was running when the problem was noticed.
 */
final class DetectionTraces {

    private static final String ROOT_BUILD_PATH = ":";

    // The prototype doesn't compute locality yet
    private static final Locality LOCALITY = Locality.UNKNOWN;

    private static final Trace ROOT_BUILD = new Trace(ImmutableList.of(new BuildFrame(ROOT_BUILD_PATH, LOCALITY)));

    private DetectionTraces() {
    }

    /**
     * Returns the trace for what was running when a problem was noticed, leaf first.
     * <ul>
     * <li>While a task runs: the task, its project, the build that owns it, and the root build.</li>
     * <li>While a project is configured: the plugin being applied, if any, the project, its build, and the root build.</li>
     * <li>Otherwise: the plugin being applied, if any, and the root build.</li>
     * </ul>
     */
    static Trace detectedIn(DetectionContext detection) {
        TaskIdentity task = detection.getTask();
        if (task != null) {
            return withBuilds(task.getBuildPath(), ImmutableList.of(
                new TaskFrame(task.getTaskPath(), LOCALITY),
                new ProjectFrame(task.getProjectPath(), LOCALITY)));
        }
        ImmutableList.Builder<Frame> leafBuilder = ImmutableList.builder();
        if (detection.getAppliedPluginId() != null) {
            leafBuilder.add(new PluginApplicationFrame(detection.getAppliedPluginId(), LOCALITY));
        }
        ProjectIdentity project = detection.getProject();
        if (project != null) {
            leafBuilder.add(new ProjectFrame(project.getBuildTreePath(), LOCALITY));
            return withBuilds(project.getBuildPath(), leafBuilder.build());
        }
        ImmutableList<Frame> leaves = leafBuilder.build();
        return leaves.isEmpty() ? ROOT_BUILD : withBuilds(ROOT_BUILD_PATH, leaves);
    }

    private static Trace withBuilds(String buildPath, ImmutableList<Frame> leaves) {
        ImmutableList.Builder<Frame> frames = ImmutableList.<Frame>builder().addAll(leaves);
        frames.add(new BuildFrame(buildPath, LOCALITY));
        // Spec Q11 is open: every build up to the root, or only the owning build. The prototype adds the root build, as UC9 does.
        if (!ROOT_BUILD_PATH.equals(buildPath)) {
            frames.add(new BuildFrame(ROOT_BUILD_PATH, LOCALITY));
        }
        return new Trace(frames.build());
    }
}
