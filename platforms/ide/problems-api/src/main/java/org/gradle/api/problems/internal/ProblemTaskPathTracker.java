/*
 * Copyright 2024 the original author or authors.
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

import javax.annotation.Nullable;

/**
 * This class tracks the currently executed task. The implementation is a naive workaround for capturing the currently executed task based on the assumption that a current thread can execute at most
 * one task at a time. The correct approach would be to capture the executed tasks via build operations (see the usages of BuildOperationAncestryTracker). We invested quite some time into it only to
 * find that introducing another ancestry tracker usage results in a significant performance regression.
 *
 * @see <a href="https://github.com/gradle/gradle/issues/31430">https://github.com/gradle/gradle/issues/31430</a>
 */
public class ProblemTaskPathTracker {

    private static final ThreadLocal<String> TASK_NAME = new ThreadLocal<String>();

    public static void setTaskIdentityPath(String path) {
        TASK_NAME.set(path);
    }

    /**
     * Returns the identity path of the currently executed task.
     *
     * @return the identity path of the currently executed task or null if no task is currently executed on the current thread.
     */
    @Nullable
    public static String getTaskIdentityPath() {
        return TASK_NAME.get();
    }

    public static void clear() {
        TASK_NAME.remove();
    }

}
