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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

/**
 * Provides access to any input files that need to be processed by an incremental work action.
 *
 * <p>
 * An incremental work action is one that accepts a single {@link InputChanges} parameter.
 * The work action can then query what changed for an input parameter since the last execution to only process the changes.
 *
 * The following example shows a task which reverses the text in each of its input files.
 * It demonstrates how to use {@link InputChanges} to only process the changed files.
 *
 * <pre class='autoTested'>
 * abstract class IncrementalReverseTask extends DefaultTask {
 *     {@literal @}Incremental
 *     {@literal @}InputDirectory
 *     abstract DirectoryProperty getInputDir()
 *
 *     {@literal @}OutputDirectory
 *     abstract DirectoryProperty getOutputDir()
 *
 *     {@literal @}TaskAction
 *     void execute(InputChanges inputChanges) {
 *         inputChanges.getFileChanges(inputDir).each { change -&gt;
 *             if (change.fileType == FileType.DIRECTORY) return
 *
 *             def targetFile = outputDir.file(change.normalizedPath).get().asFile
 *             if (change.changeType == ChangeType.REMOVED) {
 *                 targetFile.delete()
 *             } else {
 *                 targetFile.text = change.file.text.reverse()
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * In the case where Gradle is unable to determine which input files need to be reprocessed, then all of the input files will be reported as {@link ChangeType#ADDED}.
 * When such a full rebuild happens, the output files of the work are removed prior to executing the work action.
 * Cases where this occurs include:
 * <ul>
 *     <li>There is no history available from a previous execution.</li>
 *     <li>A non-file input parameter has changed since the previous execution.</li>
 *     <li>One or more output files have changed since the previous execution.</li>
 * </ul>
 *
 * @since 5.4
 */
public interface InputChanges {
    /**
     * Indicates if it was possible for Gradle to determine which input files were out of date compared to a previous execution.
     * Incremental inputs are unavailable when history is unavailable (i.e. this piece of work has never been executed before), or if there are changes to non-file input properties, or output files.
     * <p>
     * When <code>true</code>:
     * </p>
     * <ul>
     *     <li>{@link #getFileChanges(FileCollection)} and {@link #getFileChanges(Provider)} report changes to the input files compared to the previous execution.</li>
     * </ul>
     * <p>
     * When <code>false</code>:
     * </p>
     * <ul>
     *     <li>Every input file is reported via {@link #getFileChanges(FileCollection)} and {@link #getFileChanges(Provider)} as if it was {@link ChangeType#ADDED}.</li>
     * </ul>
     */
    boolean isIncremental();

    /**
     * Changes for a parameter.
     *
     * <p>When {@link #isIncremental()} is {@code false}, then all elements of the parameter are returned as {@link ChangeType#ADDED}.</p>
     *
     * <p>
     *     Only input file properties annotated with {@literal @}{@link Incremental} or {@literal @}{@link org.gradle.api.tasks.SkipWhenEmpty} can be queried for changes.
     * </p>
     *
     * <p>
     *     Note that for inputs with {@link org.gradle.api.tasks.PathSensitivity#NONE}, instead of a {@link ChangeType#MODIFIED} event,
     *     file modifications can be reported as a pair of an {@link ChangeType#ADDED} and a {@link ChangeType#REMOVED} event.
     * </p>
     *
     * @param parameter The value of the parameter to query.
     */
    Iterable<FileChange> getFileChanges(FileCollection parameter);

    /**
     * Changes for a parameter.
     *
     * <p>When {@link #isIncremental()} is {@code false}, then all elements of the parameter are returned as {@link ChangeType#ADDED}.</p>
     *
     * <p>
     *     This method allows querying properties of type {@link org.gradle.api.file.RegularFileProperty} and {@link org.gradle.api.file.DirectoryProperty} for changes.
     *     These two types are typically used for {@literal @}{@link org.gradle.api.tasks.InputFile} and {@literal @}{@link org.gradle.api.tasks.InputDirectory} properties.
     * </p>
     *
     * <p>
     *     Only input file properties annotated with {@literal @}{@link Incremental} or {@literal @}{@link org.gradle.api.tasks.SkipWhenEmpty} can be queried for changes.
     * </p>
     *
     * <p>
     *     Note that for inputs with {@link org.gradle.api.tasks.PathSensitivity#NONE}, instead of a {@link ChangeType#MODIFIED} event,
     *     file modifications can be reported as a pair of an {@link ChangeType#ADDED} and a {@link ChangeType#REMOVED} event.
     * </p>
     *
     * @param parameter The value of the parameter to query.
     */
    Iterable<FileChange> getFileChanges(Provider<? extends FileSystemLocation> parameter);
}
