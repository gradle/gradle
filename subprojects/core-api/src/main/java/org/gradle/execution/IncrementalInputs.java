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

package org.gradle.execution;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.incremental.InputFileDetails;

/**
 * Incremental inputs to work actions.
 *
 * <p>Allows querying changes of individual incremental input properties.</p>
 *
 * @since 5.4
 */
@Incubating
public interface IncrementalInputs {
    /**
     * Indicates if it was possible for Gradle to determine which exactly input files were out of date compared to a previous execution.
     * This is <em>not</em> possible in the case of no previous execution, changed input properties, output files, etc.
     * <p>
     * When <code>true</code>:
     * </p>
     * <ul>
     *     <li>{@link #getChanges(Object)} will report changes to the input files compared to the last execution.</li>
     * </ul>
     * <p>
     * When <code>false</code>:
     * </p>
     * <ul>
     *     <li>Every input file will be considered to be 'added' and will be reported to {@link #getChanges(Object)}.</li>
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
