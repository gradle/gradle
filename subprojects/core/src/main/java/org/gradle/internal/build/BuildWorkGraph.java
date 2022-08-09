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

import org.gradle.internal.concurrent.Stoppable;

import java.util.Collection;
import java.util.function.Consumer;

public interface BuildWorkGraph extends Stoppable {
    /**
     * Schedules the given tasks and all of their dependencies in this work graph.
     */
    boolean schedule(Collection<ExportedTaskNode> taskNodes);

    /**
     * Adds tasks and other nodes to this work graph.
     */
    void populateWorkGraph(Consumer<? super BuildLifecycleController.WorkGraphBuilder> action);

    /**
     * Finalize the work graph for execution, after all work has been scheduled. This method should not schedule any additional work.
     */
    void finalizeGraph();

    /**
     * Runs all work in this graph.
     */
    ExecutionResult<Void> runWork();
}
