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

package org.gradle.api.execution;

import org.gradle.api.Action;

import java.io.File;

/**
 * Provides task state information to incremental task implementations.
 */
public interface TaskExecutionContext {

    /**
     * Specifies if incremental build is not possible due to changed Input Properties, Output Files, etc.
     * In this case, every file will be considered to be 'added'.
     */
    boolean isRebuild();

    /**
     * Provides access to all of the changes made to input files since the previous task execution.
     */
    void inputFileChanges(Action<InputFileChange> action);

    /**
     * A change to an input file.
     */
    interface InputFileChange {
        /**
         * Was the file removed?
         * @return if the file was removed
         */
        boolean isRemoved();

        /**
         * Was the file added?
         * @return if the file was added
         */
        boolean isAdded();

        /**
         * Was the file modified?
         * @return if the file was modified
         */
        boolean isModified();

        /**
         * The input file, which may no longer exist.
         * @return the input file
         */
        File getFile();
    }
}
