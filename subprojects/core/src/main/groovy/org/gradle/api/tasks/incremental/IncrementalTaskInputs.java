/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks.incremental;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;

/**
 * Provides access to any input files that need to be processed by an incremental task.
 * <p>
 * An incremental task action is one that accepts a single {@link IncrementalTaskInputs} parameter.
 * The task can then provide an action to execute for all input files that are out of date with respect to the previous execution of the task,
 * and a separate action for all input files that have been removed since the previous execution.
 *
 * <pre autoTested="true">
 * class IncrementalReverseTask extends DefaultTask {
 *      @InputDirectory
 *      def File inputDir
 *
 *      @OutputDirectory
 *      def File outputDir
 *
 *      @TaskAction
 *      void execute(IncrementalTaskInputs inputs) {
 *          inputs.outOfDate { change ->
 *              def targetFile = project.file("$outputDir/${change.file.name}")
 *              targetFile.text = change.file.text.reverse()
 *          }
 *
 *          inputs.removed { change ->
 *              def targetFile = project.file("$outputDir/${change.file.name}")
 *              if (targetFile.exists()) {
 *                  targetFile.delete()
 *              }
 *          }
 *      }
 *  }
 * </pre>
 *
 * <p>
 * In the case where Gradle is unable to determine which input files need to be reprocessed, then all of the input files will be reported as {@link #outOfDate}.
 * Cases where this occurs include:
 * <ul>
 *     <li>There is no history available from a previous execution.</li>
 *     <li>An {@link org.gradle.api.tasks.TaskOutputs#upToDateWhen(groovy.lang.Closure)} criteria added to the task returns <code>false</code>.</li>
 *     <li>An {@link org.gradle.api.tasks.Input} property has changed since the previous execution.</li>
 *     <li>One or more output files have changed since the previous execution.</li>
 * </ul>
 *
 * Note that this is a stateful API:
 * <ul>
 *     <li>{@link #outOfDate} and {@link #removed} can each only be executed a single time per {@link IncrementalTaskInputs} instance.</li>
 *     <li>{@link #outOfDate} must be executed before {@link #removed} is called.</li>
 * </ul>
 */
@Incubating
@NonExtensible
public interface IncrementalTaskInputs {
    /**
     * Indicates if it was possible for Gradle to determine which exactly which input files were out of date compared to a previous execution.
     * This is <em>not</em> possible in the case of no previous execution, changed Input Properties, Output Files, etc.
     * <p>
     * When <code>true</code>:
     * <ul>
     *     <li>Any input file that has been added or modified since previous execution will be considered 'out-of-date' and reported to {@link #outOfDate}.</li>
     *     <li>Any input files that has been removed since previous execution will be reported to {@link #removed}.</li>
     * </ul>
     * </p>
     * <p>
     * When <code>false</code>:
     * <ul>
     *     <li>Every input file will be considered to be 'out-of-date' and will be reported to {@link #outOfDate}.</li>
     *     <li>No input files will be reported to {@link #removed}.</li>
     * </ul>
     * </p>
     */
    boolean isIncremental();

    /**
     * Executes the action for all of the input files that are out-of-date since the previous task execution. The action may also be supplied as a {@link groovy.lang.Closure}.
     * <ul>
     *     <li>When {@link #isIncremental()} == <code>true</code>, the action will be executed for any added or modified input file.</li>
     *     <li>When {@link #isIncremental()} == <code>false</code>, the action will be executed for every input file for the task.</li>
     * </ul>
     * <p>
     * This method may only be called a single time for a single {@link IncrementalTaskInputs} instance.
     * </p>
     * @throws IllegalStateException on second and subsequent invocations.
     */
    void outOfDate(Action<? super InputFileDetails> outOfDateAction);

    /**
     * Executes the action for all of the input files that were removed since the previous task execution. The action may also be supplied as a {@link groovy.lang.Closure}.
     * <ul>
     *     <li>When {@link #isIncremental()} == <code>true</code>, the action will be executed for any removed input file.</li>
     *     <li>When {@link #isIncremental()} == <code>false</code>, the action will not be executed.</li>
     * </ul>
     * <p>
     * This method may only be called a single time for a single {@link IncrementalTaskInputs} instance.
     * </p><p>
     * This method may only be called after {@link #outOfDate} has been called.
     * </p>
     * @throws IllegalStateException if invoked prior to {@link #outOfDate}, or if invoked more than once.
     */
    void removed(Action<? super InputFileDetails> removedAction);

}
