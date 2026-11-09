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
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.api.problems.internal.location.BuildFrame;
import org.gradle.api.problems.internal.location.Frame;
import org.gradle.api.problems.internal.location.Locality;
import org.gradle.api.problems.internal.location.ProjectFrame;
import org.gradle.api.problems.internal.location.TaskFrame;
import org.gradle.api.problems.internal.location.Trace;
import org.jspecify.annotations.Nullable;

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
     * Returns the trace for a problem noticed while the given task was running: the task, its project, the build that owns it, and the root build.
     * Without a task the trace holds the root build alone.
     */
    static Trace detectedIn(@Nullable TaskIdentity task) {
        if (task == null) {
            return ROOT_BUILD;
        }
        ImmutableList.Builder<Frame> frames = ImmutableList.builder();
        frames.add(new TaskFrame(task.getTaskPath(), LOCALITY));
        frames.add(new ProjectFrame(task.getProjectPath(), LOCALITY));
        frames.add(new BuildFrame(task.getBuildPath(), LOCALITY));
        // Spec Q11 is open: every build up to the root, or only the owning build. The prototype adds the root build, as UC9 does.
        if (!ROOT_BUILD_PATH.equals(task.getBuildPath())) {
            frames.add(new BuildFrame(ROOT_BUILD_PATH, LOCALITY));
        }
        return new Trace(frames.build());
    }
}
