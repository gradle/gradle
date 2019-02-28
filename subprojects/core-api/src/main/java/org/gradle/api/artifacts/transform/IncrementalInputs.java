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

package org.gradle.api.artifacts.transform;

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
     * Whether the input are incremental or not.
     *
     * <p>When the inputs are not incremental, then all the work needs to be re-done and cannot rely on the previous state.</p>
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
