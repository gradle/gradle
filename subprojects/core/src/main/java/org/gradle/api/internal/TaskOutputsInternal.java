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

package org.gradle.api.internal;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;

import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Callable;

public interface TaskOutputsInternal extends TaskOutputs {

    /**
     * Register some named outputs for this task.
     *
     * @param paths A {@link Callable} returning the actual output files. The keys of the returned map should not
     * be {@code null}, and they must be
     * <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">valid Java identifiers</a>}.
     * The values will be evaluated to individual files as per {@link org.gradle.api.Project#file(Object)}.
     */
    TaskOutputFilePropertyBuilder namedFiles(Callable<Map<?, ?>> paths);

    /**
     * Register some named outputs for this task.
     *
     * @param paths The output files. The keys of the map should not be {@code null}, and they must be
     * <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">valid Java identifiers</a>}.
     * The values will be evaluated to individual files as per {@link org.gradle.api.Project#file(Object)}.
     */
    TaskOutputFilePropertyBuilder namedFiles(Map<?, ?> paths);

    /**
     * Register some named outputs for this task.
     *
     * @param paths A {@link Callable} returning the actual output directories. The keys of the returned map should not
     * be {@code null}, and they must be
     * <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">valid Java identifiers</a>}.
     * The values will be evaluated to individual files as per {@link org.gradle.api.Project#file(Object)}.
     */
    TaskOutputFilePropertyBuilder namedDirectories(Callable<Map<?, ?>> paths);

    /**
     * Register some named outputs for this task.
     *
     * @param paths The output directories. The keys of the map should not be {@code null}, and they must be
     * <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">valid Java identifiers</a>}.
     * The values will be evaluated to individual files as per {@link org.gradle.api.Project#file(Object)}.
     */
    TaskOutputFilePropertyBuilder namedDirectories(Map<?, ?> paths);

    // TODO:LPTR Use this Javadoc for cacheIf() when doNotCacheIf() goes public.
    /**
     * <p>Cache the results of the task only if the given spec is satisfied. The spec will be evaluated at task execution time, not
     * during configuration. If the Spec is not satisfied, the results of the task will not be cached.</p>
     *
     * <p>You may add multiple such predicates. The results of the task are not cached if any of the predicates return {@code false},
     * or if any of the predicates passed to {@link #doNotCacheIf(Spec)} returns {@code true}.</p>
     *
     * @param spec specifies if the results of the task should be cached.
     *
     * @since 3.0
     */
    @Override
    void cacheIf(Spec<? super Task> spec);

    /**
     * <p>Disable caching the results of the task if the given spec is satisfied. The spec will be evaluated at task execution time, not
     * during configuration. If the Spec is not satisfied, the results of the task will be cached according to {@link #cacheIf(Spec)}.</p>
     *
     * <p>You may add multiple such predicates. The results of the task are not cached if any of the predicates return {@code true},
     * or if any of the predicates passed to {@link #cacheIf(Spec)} returns {@code false}.</p>
     *
     * @param spec specifies if the results of the task should not be cached.
     */
    void doNotCacheIf(Spec<? super Task> spec);

    Spec<? super TaskInternal> getUpToDateSpec();

    SortedSet<TaskOutputFilePropertySpec> getFileProperties();

    /**
     * Returns the output files recorded during the previous execution of the task.
     */
    FileCollection getPreviousOutputFiles();

    void setHistory(TaskExecutionHistory history);

    /**
     * Check if caching is explicitly enabled for the task outputs.
     */
    boolean isCacheEnabled();

    /**
     * Returns whether the task has declared any outputs.
     */
    boolean hasDeclaredOutputs();

    /**
     * Returns {@code false} if the task declares any multiple-output properties via {@link #files(Object...)},
     * {@literal @}{@link org.gradle.api.tasks.OutputFiles} or
     * {@literal @}{@link org.gradle.api.tasks.OutputDirectories}; or {@code true} otherwise.
     */
    boolean isCacheAllowed();
}
