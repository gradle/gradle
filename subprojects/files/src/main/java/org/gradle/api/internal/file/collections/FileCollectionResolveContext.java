/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.internal.file.PathToFileResolver;

public interface FileCollectionResolveContext {
    /**
     * Adds the given element to be resolved. Handles the following types:
     *
     * <ul>
     *     <li>{@link Iterable} or array - elements are recursively resolved.
     *     <li>{@link groovy.lang.Closure} - return value is recursively resolved, if not null.
     *     <li>{@link java.util.concurrent.Callable} - return value is recursively resolved, if not null.
     *     <li>{@link org.gradle.api.file.FileCollection} - resolved as is.
     *     <li>{@link org.gradle.api.Task} - resolved to task.outputs.files
     *     <li>{@link org.gradle.api.tasks.TaskOutputs} - resolved to outputs.files
     *     <li>{@link MinimalFileSet} - wrapped as a {@link org.gradle.api.file.FileCollection}.
     *     <li>{@link MinimalFileTree} - wrapped as a {@link org.gradle.api.file.FileTree}.
     *     <li>{@link FileCollectionContainer} - recursively resolved.
     *     <li>{@link org.gradle.api.tasks.TaskDependency} - resolved to an empty {@link org.gradle.api.file.FileCollection} which is builtBy the given dependency.
     *     <li>Everything else - resolved to a File and wrapped in a singleton {@link org.gradle.api.file.FileCollection}.
     * </ul>
     *
     * Generally, the result of resolution is a composite {@link org.gradle.api.file.FileCollection} which contains the union of all files and dependencies add to this context.
     *
     * @param element The element to add.
     * @return this
     */
    FileCollectionResolveContext add(Object element);

    /**
     * Adds a nested context which resolves elements using the given resolver. Any element added to the returned context will be added to this context. Those elements
     * which need to be resolved using a file resolver will use the provided resolver, instead of the default used by this context.
     */
    FileCollectionResolveContext push(PathToFileResolver fileResolver);

    /**
     * Creates a new context which can be used to resolve element. Elements added to the returned context will not be added to this context. Instead, the caller should use
     * one of {@link ResolvableFileCollectionResolveContext#resolveAsFileCollections()} or {@link ResolvableFileCollectionResolveContext#resolveAsFileTrees()}.
     */
    ResolvableFileCollectionResolveContext newContext();
}
