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

import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;

/**
 * Allows the work graph for a particular build in the build tree to be populated and executed.
 */
@ServiceScope(Scopes.Build.class)
public interface BuildWorkGraphController {
    /**
     * Locates a future task node in this build's work graph, for use from some other build's work graph.
     *
     * <p>This method does not schedule the task for execution, use {@link BuildWorkGraph#schedule(Collection)} to schedule the task.
     */
    ExportedTaskNode locateTask(TaskIdentifier taskIdentifier);

    /**
     * Creates a new, empty work graph for this build.
     *
     * Note: Only one graph can be in use at any given time. This method blocks if some other thread is using a graph for this build.
     * Eventually, this constraint should be removed, so that it is possible to populate and run multiple work graphs concurrently.
     */
    BuildWorkGraph newWorkGraph();
}
