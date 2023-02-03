/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.internal.HasInternalProtocol;

/**
 * <p>A {@code TaskOutputs} represents the outputs of a task.</p>
 *
 * <p>You can obtain a {@code TaskOutputs} instance using {@link org.gradle.api.Task#getOutputs()}.</p>
 */
@HasInternalProtocol
public interface TaskOutputs {
    /**
     * <p>
     *     Adds a predicate to determine whether previous outputs of this task can be reused.
     *     The given closure is executed at task execution time.
     *     The closure is passed the task as a parameter.
     *     If the closure returns false, previous outputs of this task cannot be reused and the task will be executed.
     *     That means the task is out-of-date and no outputs will be loaded from the build cache.
     * </p>
     *
     * <p>
     *     You can add multiple such predicates.
     *     The task outputs cannot be reused when any predicate returns false.
     * </p>
     *
     * @param upToDateClosure The closure to use to determine whether the task outputs are up-to-date.
     */
    void upToDateWhen(Closure upToDateClosure);

    /**
     * <p>
     *     Adds a predicate to determine whether previous outputs of this task can be reused.
     *     The given spec is evaluated at task execution time.
     *     If the spec returns false, previous outputs of this task cannot be reused and the task will be executed.
     *     That means the task is out-of-date and no outputs will be loaded from the build cache.
     * </p>
     *
     * <p>
     *     You can add multiple such predicates.
     *     The task outputs cannot be reused when any predicate returns false.
     * </p>
     *
     * @param upToDateSpec The spec to use to determine whether the task outputs are up-to-date.
     */
    void upToDateWhen(Spec<? super Task> upToDateSpec);

    /**
     * <p>Cache the results of the task only if the given spec is satisfied. If the spec is not satisfied,
     * the results of the task will not be cached.</p>
     *
     * <p>You may add multiple such predicates. The results of the task are not cached if any of the predicates return {@code false},
     * or if any of the predicates passed to {@link #doNotCacheIf(String, Spec)} returns {@code true}. If {@code cacheIf()} is not specified,
     * the task will not be cached unless the {@literal @}{@link CacheableTask} annotation is present on the task type.</p>
     *
     * <p>Consider using {@link #cacheIf(String, Spec)} instead for also providing a reason for enabling caching.</p>
     *
     * @param spec specifies if the results of the task should be cached.
     *
     * @since 3.0
     */
    void cacheIf(Spec<? super Task> spec);

    /**
     * <p>Cache the results of the task only if the given spec is satisfied. If the spec is not satisfied,
     * the results of the task will not be cached.</p>
     *
     * <p>You may add multiple such predicates. The results of the task are not cached if any of the predicates return {@code false},
     * or if any of the predicates passed to {@link #doNotCacheIf(String, Spec)} returns {@code true}. If {@code cacheIf()} is not specified,
     * the task will not be cached unless the {@literal @}{@link CacheableTask} annotation is present on the task type.</p>
     *
     * @param cachingEnabledReason the reason why caching would be enabled by the spec.
     * @param spec specifies if the results of the task should be cached.
     *
     * @since 3.4
     */
    void cacheIf(String cachingEnabledReason, final Spec<? super Task> spec);

    /**
     * <p>Disable caching the results of the task if the given spec is satisfied. The spec will be evaluated at task execution time, not
     * during configuration.</p>
     *
     * <p>As opposed to {@link #cacheIf(String, Spec)}, this method never enables caching for a task, it can only be used to disable caching.</p>
     *
     * <p>You may add multiple such predicates. The results of the task are not cached if any of the predicates return {@code true},
     * or if any of the predicates passed to {@link #cacheIf(String, Spec)} returns {@code false}.</p>
     *
     * @param cachingDisabledReason the reason why caching would be disabled by the spec.
     * @param spec specifies if the results of the task should not be cached.
     *
     * @since 3.4
     */
    void doNotCacheIf(String cachingDisabledReason, Spec<? super Task> spec);

    /**
     * Returns true if this task has declared any outputs. Note that a task may be able to produce output files and
     * still have an empty set of output files.
     *
     * @return true if this task has declared any outputs, otherwise false.
     */
    boolean getHasOutput();

    /**
     * Returns the output files of this task.
     *
     * @return The output files. Returns an empty collection if this task has no output files.
     */
    FileCollection getFiles();

    /**
     * Registers some output files for this task.
     *
     * <p>When the given {@code paths} is a {@link java.util.Map}, then each output file
     * will be associated with an identity.
     * The keys of the map must be non-empty strings.
     * The values of the map will be evaluated to individual files as per
     * {@link org.gradle.api.Project#file(Object)}.</p>
     *
     * <p>Otherwise the given files will be evaluated as per
     * {@link org.gradle.api.Project#files(Object...)}.</p>
     *
     * @param paths The output files.
     *
     * @see CacheableTask
     */
    TaskOutputFilePropertyBuilder files(Object... paths);

    /**
     * Registers some output directories for this task.
     *
     * <p>When the given {@code paths} is a {@link java.util.Map}, then each output directory
     * will be associated with an identity.
     * The keys of the map must be non-empty strings.
     * The values of the map will be evaluated to individual directories as per
     * {@link org.gradle.api.Project#file(Object)}.</p>
     *
     * <p>Otherwise the given directories will be evaluated as per
     * {@link org.gradle.api.Project#files(Object...)}.</p>
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
