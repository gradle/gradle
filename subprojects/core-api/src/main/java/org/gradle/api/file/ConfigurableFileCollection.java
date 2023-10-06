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
import org.gradle.api.Transformer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.HasConfigurableValue;

import java.util.Set;

/**
 * <p>A {@code ConfigurableFileCollection} is a mutable {@code FileCollection}.</p>
 *
 * <p>You can obtain an instance of {@code ConfigurableFileCollection} by calling {@link org.gradle.api.Project#files(Object...)} or {@link ObjectFactory#fileCollection()}.</p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.</p>
 */
@SupportsKotlinAssignmentOverloading
public interface ConfigurableFileCollection extends FileCollection, HasConfigurableValue {
    /**
     * Returns the set of source paths for this collection. The paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @return The set of source paths. Returns an empty set if none.
     */
    Set<Object> getFrom();

    /**
     * Sets the source paths for this collection. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param paths The paths.
     */
    void setFrom(Iterable<?> paths);

    /**
     * Sets the source paths for this collection. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param paths The paths.
     */
    void setFrom(Object... paths);

    /**
     * Adds a set of source paths to this collection. The given paths are evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     *
     * @param paths The files to add.
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

    /**
     * Applies the provided transform to the current contents of this collection and replaces content with the result.
     * The {@link FileCollection} argument of the transformer contains the same files as this collection but doesn't reflect further modifications to it.
     * Because of this it is safe to build return value of the transformer upon it.
     * <p>
     * This method can be used to avoid circular dependencies. For example, the code:
     * <pre>
     *     val collection = files("a.txt")
     *     val prefix = files("1.txt")
     *     collection = prefix + collection  // self-referencing expression, deprecated.
     * </pre>
     * Can be rewritten with this method as:
     * <pre>
     *     val collection = files("a.txt")
     *     val prefix = files("1.txt")
     *     collection.update { oldValue -&gt; prefix + oldValue }
     * </pre>
     * <p>
     * This method is somewhat equivalent to {@code setFrom(transformer.transform(files(new LinkedHashSet<>(getFiles())))} but doesn't resolve contents of this collection.
     * <p>
     * NOTE: this method evaluates transformer eagerly.
     *
     * @param transformer the transformer. If the transformer returns {@code null}, then this collection is cleared.
     * @return this
     *
     * @since 8.5
     */
    @Incubating
    // TODO(mlopatkin) should we provide ConfigurableFileCollection as an argument to the transformer?
    //    * it may be convenient to be able to modify some stuff in-place
    //    * it may be a source of confusion if the user decides they have to use it as an "output parameter".
    ConfigurableFileCollection update(Transformer<? extends FileCollection, ? super FileCollection> transformer);
}
