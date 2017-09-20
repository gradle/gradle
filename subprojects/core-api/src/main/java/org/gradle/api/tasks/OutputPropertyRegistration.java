/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

/**
 * Allows to register outputs.
 */
@NonNullApi
public interface OutputPropertyRegistration {

    /**
     * Returns true if this task has declared any outputs. Note that a task may be able to produce output files and
     * still have an empty set of output files.
     *
     * @return true if this task has declared any outputs, otherwise false.
     */
    boolean getHasOutput();

    /**
     * Registers some output files for this task.
     *
     * <p>When the given {@code paths} is a {@link java.util.Map}, then each output file
     * will be associated with an identity. For cacheable tasks this is a requirement.
     * The keys of the map must be <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">valid Java identifiers</a>.
     * The values of the map will be evaluated to individual files as per
     * {@link org.gradle.api.Project#file(Object)}.</p>
     *
     * <p>Otherwise the given files will be evaluated as per {@link org.gradle.api.Project#files(Object...)},
     * and task output caching will be disabled for the task.</p>
     *
     * @param paths The output files.
     *
     * @see CacheableTask
     */
    TaskOutputFilePropertyBuilder files(@Nullable Object... paths);

    /**
     * Registers some output directories for this task.
     *
     * <p>When the given {@code paths} is a {@link java.util.Map}, then each output directory
     * will be associated with an identity. For cacheable tasks this is a requirement.
     * The keys of the map must be <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">valid Java identifiers</a>.
     * The values of the map will be evaluated to individual directories as per
     * {@link org.gradle.api.Project#file(Object)}.</p>
     *
     * <p>Otherwise the given directories will be evaluated as per {@link org.gradle.api.Project#files(Object...)},
     * and task output caching will be disabled for the task.</p>
     *
     * @param paths The output files.
     *
     * @see CacheableTask
     *
     * @since 3.3
     */
    TaskOutputFilePropertyBuilder dirs(Object... paths);

    /**
     * Registers some output file for this task.
     *
     * @param path The output file. The given path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return a property builder to further configure this property.
     */
    TaskOutputFilePropertyBuilder file(Object path);

    /**
     * Registers an output directory for this task.
     *
     * @param path The output directory. The given path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     * @return a property builder to further configure this property.
     */
    TaskOutputFilePropertyBuilder dir(Object path);

}
