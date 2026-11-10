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

package org.gradle.api.problems.internal;

import org.gradle.internal.code.UserCodeSource;
import org.jspecify.annotations.Nullable;

/**
 * What was running when a problem was reported: the executing task, the project being configured, and the plugin being applied.
 * Each part is null when there is no such thing.
 */
public class DetectionContext {

    public static final DetectionContext NONE = new DetectionContext(null, null, null);

    private final TaskIdentity task;
    private final ProjectIdentity project;
    private final UserCodeSource.Binary appliedPlugin;

    public DetectionContext(@Nullable TaskIdentity task, @Nullable ProjectIdentity project, UserCodeSource.@Nullable Binary appliedPlugin) {
        this.task = task;
        this.project = project;
        this.appliedPlugin = appliedPlugin;
    }

    @Nullable
    public TaskIdentity getTask() {
        return task;
    }

    @Nullable
    public ProjectIdentity getProject() {
        return project;
    }

    /**
     * The plugin whose code is running, if it has an id.
     */
    public UserCodeSource.@Nullable Binary getAppliedPlugin() {
        return appliedPlugin;
    }
}
