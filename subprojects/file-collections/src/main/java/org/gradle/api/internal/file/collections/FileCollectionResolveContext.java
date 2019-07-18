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

import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.internal.file.PathToFileResolver;

public interface FileCollectionResolveContext {
    /**
     * Adds the given element to be resolved. Handles the following types:
     *
     * <ul>
     *     <li>{@link org.gradle.api.file.FileCollection} - resolved as is.
     *     <li>{@link MinimalFileSet} - wrapped as a {@link org.gradle.api.file.FileCollection}.
     *     <li>{@link MinimalFileTree} - wrapped as a {@link org.gradle.api.file.FileTree}.
     *     <li>{@link FileCollectionContainer} - recursively resolved.
     *     <li>{@link TaskDependencyContainer} - resolved to an empty {@link org.gradle.api.file.FileCollection} which is builtBy the given dependency.
     * </ul>
     *
     * Generally, the result of resolution is a composite {@link org.gradle.api.file.FileCollection} which contains the union of all files and dependencies added to this context.
     *
     * @param element The element to add.
     * @return this
     */
    FileCollectionResolveContext add(Object element);

    /**
     * Adds an element that may contribute task dependencies, but not necessarily contribute files. Handles the following types:
     *
     * <ul>
     *     <li>{@link ProviderInternal}</li>
     *     <li>{@link org.gradle.api.Buildable}</li>
     *     <li>{@link TaskDependencyContainer}</li>
     * </ul>
     *
     * @return true when the element has been handled, false when the caller is responsible for unpacking the element.
     */
    boolean maybeAdd(Object element);

    /**
     * Adds a collection of elements, as for {@link #add(Object)}.
     */
    FileCollectionResolveContext addAll(Iterable<?> elements);

    /**
     * Adds a single element to be resolved to a file.
     */
    FileCollectionResolveContext add(Object element, PathToFileResolver resolver);

    /**
     * Creates a new context which can be used to resolve element. Elements added to the returned context will not be added to this context. Instead, the caller should use
     * one of {@link ResolvableFileCollectionResolveContext#resolveAsFileCollections()} or {@link ResolvableFileCollectionResolveContext#resolveAsFileTrees()}.
     */
    ResolvableFileCollectionResolveContext newContext();
}
