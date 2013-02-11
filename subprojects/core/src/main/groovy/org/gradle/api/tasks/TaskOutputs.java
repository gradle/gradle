/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;

/**
 * <p>A {@code TaskOutputs} represents the outputs of a task.</p>
 *
 * <p>You can obtain a {@code TaskOutputs} instance using {@link org.gradle.api.Task#getOutputs()}.</p>
 */
public interface TaskOutputs {
    /**
     * <p>Adds a predicate to determine whether the outputs of this task are up-to-date. The given closure is executed
     * at task execution time. The closure is passed the task as a parameter. If the closure returns false, the task
     * outputs are considered out-of-date and the task will be executed.</p>
     *
     * <p>You can add multiple such predicates. The task outputs are considered out-of-date when any predicate returns
     * false.<p>
     *
     * @param upToDateClosure The closure to use to determine whether the task outputs are up-to-date.
     */
    void upToDateWhen(Closure upToDateClosure);

    /**
     * <p>Adds a predicate to determine whether the outputs of this task are up-to-date. The given spec is evaluated at
     * task execution time. If the spec returns false, the task outputs are considered out-of-date and the task will be
     * executed.</p>
     *
     * <p>You can add multiple such predicates. The task outputs are considered out-of-date when any predicate returns
     * false.<p>
     *
     * @param upToDateSpec The spec to use to determine whether the task outputs are up-to-date.
     */
    void upToDateWhen(Spec<? super Task> upToDateSpec);

    /**
     * Returns true if this task has declared any outputs. Note that a task may be able to produce output files and
     * still have an empty set of output files.
     *
     * @return true if this task has declared any outputs, otherwise false.
     */
    boolean getHasOutput();

    /**
     * Returns the output files of this task.
     *
     * @return The output files. Returns an empty collection if this task has no output files.
     */
    FileCollection getFiles();

    /**
     * Registers some output files for this task.
     *
     * @param paths The output files. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     * @return this
     */
    TaskOutputs files(Object... paths);

    /**
     * Registers some output file for this task.
     *
     * @param path The output file. The given path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return this
     */
    TaskOutputs file(Object path);

    /**
     * Registers an output directory for this task.
     *
     * @param path The output directory. The given path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return this
     */
    TaskOutputs dir(Object path);
}
