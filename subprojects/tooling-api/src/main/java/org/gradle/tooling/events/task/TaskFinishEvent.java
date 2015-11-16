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

package org.gradle.tooling.events.task;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.FinishEvent;

/**
 * An event that informs about a task having finished its execution. You can query the result of the task using {@link #getResult()}.
 *
 * @since 2.5
 */
@Incubating
public interface TaskFinishEvent extends TaskProgressEvent, FinishEvent {

    /**
     * Returns the result of the finished task operation. Currently, the result will be one of the following sub-types:
     *
     * <ul>
     *     <li>{@link TaskSuccessResult}</li>
     *     <li>{@link TaskSkippedResult}</li>
     *     <li>{@link TaskFailureResult}</li>
     * </ul>
     *
     * @return the result of the finished task operation
     */
    @Override
    TaskOperationResult getResult();

}
