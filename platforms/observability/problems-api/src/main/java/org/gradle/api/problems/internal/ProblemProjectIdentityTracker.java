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

import org.jspecify.annotations.Nullable;

/**
 * Tracks the project the current thread is configuring, the way {@link ProblemTaskIdentityTracker} tracks the executing task.
 * A thread-local for the same reason: looking the project up through the build operation ancestry costs too much on the reporting path.
 */
public class ProblemProjectIdentityTracker {

    private static final ThreadLocal<ProjectIdentity> PROJECT_IDENTITY = new ThreadLocal<ProjectIdentity>();

    public static void setProjectIdentity(@Nullable ProjectIdentity projectIdentity) {
        if (projectIdentity == null) {
            PROJECT_IDENTITY.remove();
        } else {
            PROJECT_IDENTITY.set(projectIdentity);
        }
    }

    /**
     * Returns the identity of the project the current thread is configuring, or null if it isn't configuring one.
     */
    @Nullable
    public static ProjectIdentity getProjectIdentity() {
        return PROJECT_IDENTITY.get();
    }
}
