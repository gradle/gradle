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

package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.NonNullApi;

/**
 * Represents the complete changes in a tasks state
 */
@NonNullApi
public interface TaskUpToDateState {

    int MAX_OUT_OF_DATE_MESSAGES = 3;

    /**
     * Returns changes to input files only.
     */
    Iterable<TaskStateChange> getInputFilesChanges();

    /**
     * Returns if any output files have been changed, added or removed.
     */
    boolean hasAnyOutputFileChanges();

    /**
     * Returns any change to task inputs or outputs.
     */
    Iterable<TaskStateChange> getAllTaskChanges();

    /**
     * Returns changes that would force an incremental task to fully rebuild.
     */
    Iterable<TaskStateChange> getRebuildChanges();
}
