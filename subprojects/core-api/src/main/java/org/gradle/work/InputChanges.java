/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.work;

import org.gradle.api.Incubating;

/**
 * Provides access to any input files that need to be processed by an incremental work action.
 *
 * <p>
 * An incremental work action is one that accepts a single {@link InputChanges} parameter.
 * The work action can then query what changed for an input parameter since the last execution to only process the changes.
 *
 * <pre class='autoTested'>
 * class IncrementalReverseTask extends DefaultTask {
 *     {@literal @}InputDirectory
 *     def File inputDir
 *
 *     {@literal @}OutputDirectory
 *     def File outputDir
 *
 *     {@literal @}TaskAction
 *     void execute(InputChanges inputChanges) {
 *         if (!inputChanges.incremental) {
 *             project.delete(outputDir.listFiles())
 *         }
 *
 *         inputChanges.getFileChanges(inputDir).each { change -&gt;
 *             switch (change.changeType) {
 *                 case REMOVED:
 *                     def targetFile = project.file("$outputDir/${change.file.name}")
 *                     if (targetFile.exists()) {
 *                         targetFile.delete()
 *                     }
 *                     break
 *                 default:
 *                     def targetFile = project.file("$outputDir/${change.file.name}")
 *                     targetFile.text = change.file.text.reverse()
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * In the case where Gradle is unable to determine which input files need to be reprocessed, then all of the input files will be reported as {@link ChangeType#ADDED}.
 * Cases where this occurs include:
 * <ul>
 *     <li>There is no history available from a previous execution.</li>
 *     <li>A non-file input parameter has changed since the previous execution.</li>
 *     <li>One or more output files have changed since the previous execution.</li>
 * </ul>
 *
 * @since 5.4
 */
@Incubating
public interface InputChanges {
    /**
     * Indicates if it was possible for Gradle to determine which input files were out of date compared to a previous execution.
     * Incremental inputs are unavailable when history is unavailable (i.e. this piece of work has never been executed before), or if there are changes to non-file input properties, or output files.
     * <p>
     * When <code>true</code>:
     * </p>
     * <ul>
     *     <li>{@link #getFileChanges(Object)} reports changes to the input files compared to the previous execution.</li>
     * </ul>
     * <p>
     * When <code>false</code>:
     * </p>
     * <ul>
     *     <li>Every input file is reported via {@link #getFileChanges(Object)} as if it was {@link ChangeType#ADDED}.</li>
     * </ul>
     */
    boolean isIncremental();

    /**
     * Changes for a parameter.
     *
     * <p>When {@link #isIncremental()} is {@code false}, then all elements of the parameter are returned as {@link ChangeType#ADDED}.</p>
     *
     * <p>
     *     Only input file properties annotated with {@link Incremental} or {@link org.gradle.api.tasks.SkipWhenEmpty} can be queried for changes.
     * </p>
     *
     * @param parameterValue The value of the parameter to query.
     */
    Iterable<FileChange> getFileChanges(Object parameterValue);
}
