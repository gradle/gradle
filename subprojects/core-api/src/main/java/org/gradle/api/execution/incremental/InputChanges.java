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

package org.gradle.api.execution.incremental;

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.api.tasks.incremental.InputFileDetails;

/**
 * Provides access to any input files that need to be processed by an incremental work action.
 *
 * <p>
 * An incremental work action is one that accepts a single {@link InputChanges} parameter.
 * The work action can then query what changed for an input property since the last execution to only process the changes.
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
 *         if (!inputChanges.incremental)
 *             project.delete(outputDir.listFiles())
 *
 *         inputChanges.getChanges(inputDir).each { change -&gt;
 *             if (change.removed) {
 *                 def targetFile = project.file("$outputDir/${change.file.name}")
 *                 if (targetFile.exists()) {
 *                     targetFile.delete()
 *                 }
 *             } else {
 *                 def targetFile = project.file("$outputDir/${change.file.name}")
 *                 targetFile.text = change.file.text.reverse()
 *             }
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * In the case where Gradle is unable to determine which input files need to be reprocessed, then all of the input files will be reported as added.
 * Cases where this occurs include:
 * <ul>
 *     <li>There is no history available from a previous execution.</li>
 *     <li>An non-file input property has changed since the previous execution.</li>
 *     <li>One or more output files have changed since the previous execution.</li>
 * </ul>
 *
 * @since 5.4
 */
@NonExtensible
@Incubating
public interface InputChanges {
    /**
     * Indicates if it was possible for Gradle to determine which input files were out of date compared to a previous execution.
     * Incremental inputs are unavailable when history is unavailable (i.e. this piece of work has never been executed before), or if there are changes to non-file input properties, or output files.
     * <p>
     * When <code>true</code>:
     * </p>
     * <ul>
     *     <li>{@link #getChanges(Object)} reports changes to the input files compared to the previous execution.</li>
     * </ul>
     * <p>
     * When <code>false</code>:
     * </p>
     * <ul>
     *     <li>Every input file is reported via {@link #getChanges(Object)} as if it was 'added'.</li>
     * </ul>
     */
    boolean isIncremental();

    /**
     * Changes for the property.
     *
     * <p>When {@link #isIncremental()} is {@code false}, then all elements of the property are returned as added.</p>
     *
     * @param property The instance of the property to query.
     */
    Iterable<InputFileDetails> getChanges(Object property);
}
