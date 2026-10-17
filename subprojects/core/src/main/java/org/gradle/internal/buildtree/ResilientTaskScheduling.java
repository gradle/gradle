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

package org.gradle.internal.buildtree;

import org.gradle.internal.build.BuildState;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a task run proceed in the builds of a build tree that configured successfully when other builds of the tree did not.
 * <p>
 * Off by default. When enabled, a build that fails to configure is recorded here instead of failing the whole task graph calculation.
 * Tasks requested in such a build are dropped, tasks in the other builds run, and the recorded failures are added to the
 * result of the task run, so the build still fails.
 * <p>
 * Intended for IDE sync, where models are produced by tasks and a broken included build must not hide the models of the healthy ones.
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public class ResilientTaskScheduling {
    public static final InternalOption<Boolean> FLAG = InternalOptions.ofBoolean("org.gradle.internal.resilient-task-scheduling", false);

    private final boolean enabled;
    private final Map<BuildState, Throwable> brokenBuilds = new LinkedHashMap<>();
    private final List<Throwable> unreportedFailures = new ArrayList<>();

    public ResilientTaskScheduling(InternalOptions options) {
        this.enabled = options.getValue(FLAG);
    }

    private ResilientTaskScheduling() {
        this.enabled = false;
    }

    public static ResilientTaskScheduling disabled() {
        return new ResilientTaskScheduling();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Configures the given build if that has not happened yet, and returns whether tasks may be scheduled for it.
     * A build that fails to configure is recorded as broken and never configured again.
     */
    public boolean canScheduleTasksFor(BuildState build) {
        synchronized (this) {
            if (brokenBuilds.containsKey(build)) {
                return false;
            }
        }
        try {
            build.ensureProjectsConfigured();
            return true;
        } catch (RuntimeException e) {
            recordBrokenBuild(build, e);
            return false;
        }
    }

    public synchronized void recordBrokenBuild(BuildState build, Throwable failure) {
        brokenBuilds.putIfAbsent(build, failure);
        if (!containsSameInstance(unreportedFailures, failure)) {
            unreportedFailures.add(failure);
        }
    }

    /**
     * Returns the failures recorded since the last call and forgets them, so each failure is attached to one task run only.
     */
    public synchronized List<Throwable> takeUnreportedFailures() {
        List<Throwable> result = new ArrayList<>(unreportedFailures);
        unreportedFailures.clear();
        return result;
    }

    private static boolean containsSameInstance(List<Throwable> failures, Throwable failure) {
        for (Throwable candidate : failures) {
            if (candidate == failure) {
                return true;
            }
        }
        return false;
    }
}
