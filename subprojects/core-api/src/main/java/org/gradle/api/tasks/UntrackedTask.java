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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attached to a task to declare that the task should be untracked.
 *
 * <p>If a task is untracked, then Gradle will not try to capture its state.
 * That also means that Gradle does not do any optimizations for running the task.
 * For example, such a task will always be out of date and never stored in or loaded from the build cache.
 *
 * <p>There can be different reasons for declaring a task as untracked, for example:
 * <ul>
 *     <li>Some input or output locations contain unreadable files like pipes where Gradle cannot track the content.</li>
 *     <li>The input or output is stored remotely, for example in a database, and its state cannot be tracked.</li>
 *     <li>Another tool like Git already takes care of keeping the state, so it doesn't make sense for Gradle to do additional bookkeeping.</li>
 *     <li>Prevent Gradle from trying to snapshot a potentially large amount of content if an output location is not exclusively owned by the build.</li>
 * </ul>
 *
 * <p>{@link org.gradle.work.InputChanges} cannot be used for untracked tasks,
 * since incremental tasks need to track the state of their inputs and outputs for them to be correct.
 *
 * @since 7.3
 */
@Incubating
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface UntrackedTask {
    /**
     * Mandatory reason why the task is untracked.
     */
    String because();
}
