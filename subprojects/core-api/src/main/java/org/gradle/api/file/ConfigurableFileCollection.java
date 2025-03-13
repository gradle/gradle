/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.file;

import org.gradle.api.Incubating;
import org.gradle.api.SupportsKotlinAssignmentOverloading;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.provider.SupportsConvention;

import java.util.Set;

/**
 * <p>A {@code ConfigurableFileCollection} is a mutable {@code FileCollection}.</p>
 *
 * <p>You can obtain an instance of {@code ConfigurableFileCollection} by calling {@link org.gradle.api.Project#files(Object...)} or {@link ObjectFactory#fileCollection()}.</p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 */
@SupportsKotlinAssignmentOverloading
public interface ConfigurableFileCollection extends FileCollection, HasConfigurableValue, SupportsConvention {
    /**
     * Returns the set of source paths for this collection. The paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @return The set of source paths. Returns an empty set if none.
     */
    Set<Object> getFrom();

    /**
     * Sets the source paths for this collection. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param paths The paths. {@code null} values are ignored.
     */
    void setFrom(Iterable<?> paths);

    /**
     * Sets the source paths for this collection. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param paths The paths. {@code null} values are ignored.
     */
    void setFrom(Object... paths);

    /**
     * Specifies the value to use as the convention (default value) to be used when resolving this file collection,
     * if no source paths are explicitly defined.
     *
     * If, at the time this method is invoked, the set of source paths for this collection is empty, the convention will be used
     * to resolve this file collection.
     *
     * @param paths The paths. {@code null} values are ignored.
     * @return this collection
     *
     * @since 8.8
     */
    @Incubating
    ConfigurableFileCollection convention(Iterable<?> paths);

    /**
     * Specifies the value to use as the convention (default value) to be used when resolving this file collection,
     * if no source paths are explicitly defined.
     *
     * If, at the time this method is invoked, the set of source paths for this collection is empty, the convention will be used
     * to resolve this file collection.
     *
     * @param paths The paths. {@code null} values are ignored.
     * @return this collection
     *
     * @since 8.8
     */
    @Incubating
    ConfigurableFileCollection convention(Object... paths);

    /**
     * Adds a set of source paths to this collection. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param paths The files to add. {@code null} values are ignored.
     * @return this
     */
    ConfigurableFileCollection from(Object... paths);

    /**
     * Returns the set of tasks which build the files of this collection.
     *
     * @return The set. Returns an empty set when there are no such tasks.
     */
    Set<Object> getBuiltBy();

    /**
     * Sets the tasks which build the files of this collection.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurableFileCollection setBuiltBy(Iterable<?> tasks);

    /**
     * Registers some tasks which build the files of this collection.
     *
     * @param tasks The tasks. These are evaluated as per {@link org.gradle.api.Task#dependsOn(Object...)}.
     * @return this
     */
    ConfigurableFileCollection builtBy(Object... tasks);
}
