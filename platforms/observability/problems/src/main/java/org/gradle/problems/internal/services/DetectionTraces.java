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
import org.gradle.internal.code.Locality;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.api.problems.internal.location.PluginApplicationFrame;
import org.gradle.api.problems.internal.location.ProjectFrame;
import org.gradle.api.problems.internal.location.TaskFrame;
import org.gradle.api.problems.internal.location.Trace;

/**
 * Builds the detection trace of a problem: what was running when the problem was noticed.
 */
final class DetectionTraces {

    private static final String ROOT_BUILD_PATH = ":";

    // Projects and builds are the user's own, whichever build of the tree they belong to
    private static final Locality BUILD_MODEL = Locality.LOCAL;

    private static final Trace ROOT_BUILD = new Trace(ImmutableList.of(createRootBuildFrame()));

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
            // A task takes the locality of the code that registered it
            return withBuilds(task.getBuildPath(), ImmutableList.of(
                new TaskFrame(task.getTaskPath(), task.getLocality()),
                new ProjectFrame(task.getProjectPath(), BUILD_MODEL)));
        }
        ImmutableList.Builder<Frame> leafBuilder = ImmutableList.builder();
        UserCodeSource.Binary plugin = detection.getAppliedPlugin();
        if (plugin != null) {
            leafBuilder.add(new PluginApplicationFrame(plugin.getPluginId(), plugin.getLocality()));
        }
        ProjectIdentity project = detection.getProject();
        if (project != null) {
            leafBuilder.add(new ProjectFrame(project.getBuildTreePath(), BUILD_MODEL));
            return withBuilds(project.getBuildPath(), leafBuilder.build());
        }
        ImmutableList<Frame> leaves = leafBuilder.build();
        return leaves.isEmpty() ? ROOT_BUILD : withBuilds(ROOT_BUILD_PATH, leaves);
    }

    private static Trace withBuilds(String buildPath, ImmutableList<Frame> leaves) {
        ImmutableList.Builder<Frame> frames = ImmutableList.<Frame>builder().addAll(leaves);
        frames.add(new BuildFrame(buildPath, BUILD_MODEL));
        // Spec Q11 is open: every build up to the root, or only the owning build. The prototype adds the root build, as UC9 does.
        if (!ROOT_BUILD_PATH.equals(buildPath)) {
            frames.add(createRootBuildFrame());
        }
        return new Trace(frames.build());
    }

    private static BuildFrame createRootBuildFrame() {
        return new BuildFrame(ROOT_BUILD_PATH, BUILD_MODEL);
    }
}
