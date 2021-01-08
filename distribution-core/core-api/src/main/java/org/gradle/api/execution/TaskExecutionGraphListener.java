/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.execution;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

/**
 * <p>A {@code TaskExecutionGraphListener} is notified when the {@link TaskExecutionGraph} has been populated. You can
 * use this interface in your build file to perform some action based on the contents of the graph, before any tasks are
 * actually executed.</p>
 */
@EventScope(Scopes.Build.class)
public interface TaskExecutionGraphListener {
    /**
     * <p>This method is called when the {@link TaskExecutionGraph} has been populated, and before any tasks are
     * executed.
     *
     * @param graph The graph. Never null.
     */
    void graphPopulated(TaskExecutionGraph graph);
}
