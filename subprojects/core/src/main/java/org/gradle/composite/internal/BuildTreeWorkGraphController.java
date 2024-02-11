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

package org.gradle.composite.internal;

import org.gradle.internal.buildtree.BuildTreeWorkGraph;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nonnull;
import javax.annotation.meta.When;
import java.util.function.Function;

/**
 * A service that allows work graphs to be created, populated and executed.
 */
@ServiceScope(Scopes.BuildTree.class)
public interface BuildTreeWorkGraphController {
    /**
     * Locates a task node in another build's work graph. Does not schedule the task for execution, use {@link IncludedBuildTaskResource#queueForExecution()} to queue the task for execution.
     */
    IncludedBuildTaskResource locateTask(TaskIdentifier taskIdentifier);

    /**
     * Runs the given action against a new, empty work graph. This allows tasks to be run while calculating the task graph of the build tree, for example to run `buildSrc` tasks or
     * to build local plugins in an included build.
     */
    @Nonnull(when = When.UNKNOWN)
    <T> T withNewWorkGraph(Function<? super BuildTreeWorkGraph, T> action);
}
