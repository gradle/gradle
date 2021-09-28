/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.internal.TaskInternal;

import java.util.Collection;

/**
 * Allows the work graph for a particular build in the build tree to be populated and executed.
 */
public interface BuildWorkGraphController {
    /**
     * Locates a future task node in this build's work graph, for use from some other build's work graph.
     *
     * <p>This method does not schedule the task for execution, use {@link BuildWorkGraph#schedule(Collection)} to schedule the task.
     */
    ExportedTaskNode locateTask(TaskInternal task);

    /**
     * Locates a future task node in this build's work graph, for use from some other build's work graph.
     *
     * <p>This method does not schedule the task for execution, use {@link BuildWorkGraph#schedule(Collection)} to schedule the task.
     */
    ExportedTaskNode locateTask(String taskPath);

    /**
     * Creates a new, empty work graph for this build.
     */
    BuildWorkGraph newWorkGraph();
}
