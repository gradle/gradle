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

package org.gradle.api.tasks;

/**
 * <p>A <code>StopExecutionException</code> is thrown by a {@link org.gradle.api.Action} or task action closure to
 * stop execution of the current task and start execution of the next task. This allows, for example, precondition
 * actions to be added to a task which abort execution of the task if the preconditions are not met.</p>
 *
 * <p>Note that throwing this exception does not fail the execution of the task or the build.</p>
 */
public class StopExecutionException extends RuntimeException {

    public StopExecutionException() {
        super();
    }

    public StopExecutionException(String message) {
        super(message);
    }

}
