/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Represents a task realization for a task whose creation was deferred.
 *
 * @since 4.9
 */
public final class RegisterTaskBuildOperationType implements BuildOperationType<RegisterTaskBuildOperationType.Details, RegisterTaskBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {
        String getBuildPath();

        String getTaskPath();

        /**
         * An ID for the task, that disambiguates it from other tasks with the same path.
         *
         * Due to a bug in Gradle, two tasks with the same path can be executed.
         * This is very problematic for build scans.
         * As such, scans need to be able to differentiate between different tasks with the same path.
         * The combination of the path and ID does this.
         *
         * In later versions of Gradle, executing two tasks with the same path will be prevented
         * and this value can be noop-ed.
         */
        long getTaskId();

        boolean isReplacement();
    }

    @UsedByScanPlugin
    public interface Result {
    }

    private RegisterTaskBuildOperationType() {
    }

}
