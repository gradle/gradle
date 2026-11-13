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

package org.gradle.api.problems.internal.location;

import org.gradle.internal.code.Locality;
import org.gradle.internal.code.UserCodeSource;
import org.jspecify.annotations.Nullable;

/**
 * A task of the build.
 */
public final class TaskFrame extends Frame {

    private final String buildTreePath;
    private final @Nullable UserCodeSource registeredBy;

    public TaskFrame(String buildTreePath, Locality locality) {
        super("task " + buildTreePath, locality);
        this.buildTreePath = buildTreePath;
        this.registeredBy = null;
    }

    /**
     * A task with the record of the code that registered it. The task takes that code's locality.
     * A reporter that names a task other than the running one gives the record, so the runtime can add the task's lineage.
     */
    public TaskFrame(String buildTreePath, @Nullable UserCodeSource registeredBy) {
        super("task " + buildTreePath, registeredBy == null ? Locality.UNKNOWN : registeredBy.getLocality());
        this.buildTreePath = buildTreePath;
        this.registeredBy = registeredBy;
    }

    /**
     * The path of the task in the build tree, such as {@code :lib:core:compileJava}.
     */
    public String getBuildTreePath() {
        return buildTreePath;
    }

    /**
     * The code that registered the task, when the reporter gave it. It stays inside the build process: no consumer receives it.
     */
    public @Nullable UserCodeSource getRegisteredBy() {
        return registeredBy;
    }

    @Override
    public boolean isAuthored() {
        return false;
    }
}
