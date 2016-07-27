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
 * Helper interface for binary compatibility with Gradle 2.x version of the {@link TaskOutputs} interface.
 *
 * @deprecated The interface is only here to allow plugins built against Gradle 2.x to run and it will be removed in Gradle 4.0.
 */
@Deprecated
public interface CompatibilityAdapterForTaskOutputs {
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
