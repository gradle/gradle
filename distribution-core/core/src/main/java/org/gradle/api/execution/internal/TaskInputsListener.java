/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.execution.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope.Global;

/**
 * Registered via {@link TaskInputsListeners}.
 */
@EventScope(Global.class)
public interface TaskInputsListener {

    /**
     * Called when the execution of the given task is imminent, or would have been if the given file collection was not currently empty.
     * <p>
     * The given files may not == taskInternal.inputs.files, as only a subset of that collection may be relevant to the task execution.
     *
     * @param task the task to be executed
     * @param fileSystemInputs the file system inputs relevant to the task execution
     */
    void onExecute(TaskInternal task, FileCollectionInternal fileSystemInputs);

}
