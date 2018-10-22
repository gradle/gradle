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

import org.gradle.api.Action;
import org.gradle.api.Task;

/**
 * An action to run as soon as the given task completes, to perform some work before the outputs of the task are consumed by other tasks.
 *
 * <p>This should evolve into some real node to the graph with similar behaviour, but as a first step this is simply bolted on.
 */
public interface FinalizeAction extends Action<Task> {
    TaskDependencyContainer getDependencies();
}
