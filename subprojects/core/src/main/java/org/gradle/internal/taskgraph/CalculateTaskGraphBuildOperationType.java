/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.taskgraph;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.List;

/**
 * Computing the task graph for a given build in the build tree based on the inputs and build configuration.
 *
 * @since 4.0
 */
public final class CalculateTaskGraphBuildOperationType implements BuildOperationType<CalculateTaskGraphBuildOperationType.Details, CalculateTaskGraphBuildOperationType.Result> {

    /**
     *
     * @since 6.2
     *
     * */
    @UsedByScanPlugin
    public interface TaskIdentity {

        String getBuildPath();

        String getTaskPath();

        /**
         * @see org.gradle.api.internal.project.taskfactory.TaskIdentity#uniqueId
         */
        long getTaskId();

    }

    /**
     *
     * @since 6.2
     *
     * */
    @UsedByScanPlugin
    public interface PlannedTask {

        TaskIdentity getTask();

        List<TaskIdentity> getDependencies();

        List<TaskIdentity> getMustRunAfter();

        List<TaskIdentity> getShouldRunAfter();

        List<TaskIdentity> getFinalizedBy();

    }

    @UsedByScanPlugin
    public interface Details {

        /**
         * The build path the calculated task graph belongs too.
         * Never null.
         *
         * @since 4.5
         */
        String getBuildPath();
    }

    @UsedByScanPlugin
    public interface Result {

        /**
         * Lexicographically sorted.
         * Never null.
         * Never contains duplicates.
         */
        List<String> getRequestedTaskPaths();

        /**
         * Lexicographically sorted.
         * Never null.
         * Never contains duplicates.
         */
        List<String> getExcludedTaskPaths();

        /**
         * Capturing task execution plan details.
         *
         * @since 6.2
         */
        List<PlannedTask> getTaskPlan();
    }

    private CalculateTaskGraphBuildOperationType() {
    }

}
