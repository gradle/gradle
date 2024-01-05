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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.SupportsKotlinAssignmentOverloading;
import org.gradle.api.Transformer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ConfigurableValue;
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
public interface ConfigurableFileCollection extends FileCollection, HasConfigurableValue, ConfigurableValue<FileCollectionConfigurer>, SupportsConvention, FileCollectionConfigurer {
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
     * Specifies the value to use as the convention (default value) to be used when resolving this file collection,
     * if no source paths are explicitly defined.
     *
     * If, at the time this method is invoked, the set of source paths for this collection is empty, the convention will be used
     * to resolve this file collection.
     *
     * @param paths The paths.
     * @return this collection
     *
     * @since 8.7
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
     * @param paths The paths.
     * @return this collection
     *
     * @since 8.7
     */
    @Incubating
    ConfigurableFileCollection convention(Object... paths);

    /**
     * {@inheritDoc}
     */
    @Override
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
     * Performs incremental updates to the actual value of this file collection.
     *
     * {@inheritDoc}
     *
     * For wholesale updates to the explicit value, use
     * {@link #setFrom(Object...)}, {@link #setFrom(Iterable)} or {@link #update(Transformer)}.
     *
     * For wholesale updates to the convention value, use
     * {@link #convention(Object...)} or {@link #convention(Iterable)}.
     */
    @Override
    @Incubating
    ConfigurableFileCollection withActualValue(Action<FileCollectionConfigurer> action);

    /**
     * Performs incremental updates to the actual value of this file collection.
     *
     * This is a Groovy closure-compatible version of
     * {@link ConfigurableFileCollection#withActualValue(Action)},
     * having this file collection's actual value (and not the file collection itself)
     * as the target object.
     *
     * @param action a Groovy closure to incrementally configure this object actual value
     * via the {@link FileCollectionConfigurer} protocol.
     *
     * @see #withActualValue(Action)
     * @since 8.7
     */
    @Incubating
    ConfigurableFileCollection withActualValue(@DelegatesTo(FileCollectionConfigurer.class) Closure<Void> action);

    /**
     * Applies an eager transformation to the current contents of this file collection, without explicitly resolving it.
     * The provided transformer is applied to the file collection representing the current contents, and the returned collection is used as a new content.
     * The current contents collection can be used to derive the new value, but doesn't have to.
     * Returning null from the transformer empties this collection.
     * For example, it is possible to filter out all text files from the collection:
     * <pre class='autoTested'>
     *     def collection = files("a.txt", "b.md")
     *
     *     collection.update { it.filter { f -&gt; !f.name.endsWith(".txt") } }
     *
     *     println(collection.files) // ["b.md"]
     * </pre>
     * <p>
     * <b>Further changes to this file collection, such as calls to {@link #setFrom(Object...)} or {@link #from(Object...)}, are not transformed, and override the update instead</b>.
     * Because of this, this method inherently depends on the order of changes, and therefore must be used sparingly.
     * <p>
     * If this file collection consists of other mutable sources, then the current contents collection tracks the changes to these sources.
     * For example, changes to the upstream collection are visible:
     * <pre class='autoTested'>
     *     def upstream = files("a.txt", "b.md")
     *     def collection = files(upstream)
     *
     *     collection.update { it.filter { f -&gt; !f.name.endsWith(".txt") } }
     *     upstream.from("c.md", "d.txt")
     *
     *     println(collection.files) // ["b.md", "c.md"]
     * </pre>
     * The provided transformation runs <b>eagerly</b>, so it can capture any objects without introducing memory leaks and without breaking configuration caching.
     * However, transformations applied to the current contents collection (like {@link FileCollection#filter(Closure)}) are subject to the usual constraints.
     * <p>
     * The current contents collection inherits dependencies of this collection specified by {@link #builtBy(Object...)}.
     *
     * @param transform the transformation to apply to the current value. May return null, which empties this collection.
     * @since 8.6
     */
    @Incubating
    void update(Transformer<? extends @org.jetbrains.annotations.Nullable FileCollection, ? super FileCollection> transform);
}
