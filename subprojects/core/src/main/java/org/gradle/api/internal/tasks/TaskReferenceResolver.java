/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskReference;

/**
 * A resolver for task references, required to convert a task reference into a fully-fledged Task instance.
 *
 * It would be great if our task execution engine could natively understand task references.
 * For now we rely on synthetic delegating tasks to do the work.
 */
public interface TaskReferenceResolver {
    /**
     * Attempt to construct a task from the provided reference, returning null if not recognized.
     */
    Task constructTask(TaskReference reference, TaskContainer tasks);
}
