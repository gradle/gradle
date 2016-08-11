/*
 * Copyright 2016 the original author or authors.
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

/**
 * Helper interface for binary compatibility with Gradle 2.x version of the {@link TaskInputs} interface.
 *
 * @deprecated The interface is only here to allow plugins built against Gradle 2.x to run and it will be removed in Gradle 4.0.
 */
@Deprecated
public interface CompatibilityAdapterForTaskInputs {
    /**
     * Registers some input files for this task.
     *
     * @param paths The input files. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     * @return this
     */
    TaskInputs files(Object... paths);

    /**
     * Registers some input file for this task.
     *
     * @param path The input file. The given path is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     * @return this
     */
    TaskInputs file(Object path);

    /**
     * Registers an input directory hierarchy. All files found under the given directory are treated as input files for
     * this task.
     *
     * @param path The directory. The path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return this
     */
    TaskInputs dir(Object path);
}
