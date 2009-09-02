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
import org.gradle.api.tasks.util.PatternSet;

/**
 * A {@code FileTree} represents a read-only hierarchy of files. It extends {@code FileCollection} to add hierarchy
 * query and manipulation methods.
 */
public interface FileTree extends FileCollection {
    /**
     * <p>Restricts the contents of this tree to those files matching the given filter. The filtered tree is live, so
     * that any changes to this tree are reflected in the filtered tree.</p>
     *
     * <p>The given closure is used to configure the filter. A {@link org.gradle.api.tasks.util.PatternFilterable} is
     * passed to the closure as it's delegate. Only files which match the specified include patterns will be included in
     * the filtered tree. Any files which match the specified exclude patterns will be excluded from the filtered
     * tree.</p>
     *
     * @param filterConfigClosure the closure to use to configure the filter.
     * @return The filtered tree.
     */
    FileTree matching(Closure filterConfigClosure);

    /**
     * <p>Restricts the contents of this tree to those files matching the given filter. The filtered tree is live, so
     * that any changes to this tree are reflected in the filtered tree.</p>
     *
     * <p>The given pattern set is used to configure the filter. Only files which match the specified include patterns
     * will be included in the filtered tree. Any files which match the specified exclude patterns will be excluded from
     * the filtered tree.</p>
     *
     * @param patterns the pattern set to use to configure the filter.
     * @return The filtered tree.
     */
    FileTree matching(PatternSet patterns);

    /**
     * Returns a {@code FileTree} which contains the union of this tree and the given tree. The returned tree is live,
     * so that changes to either this tree or the other source tree are reflected in the returned tree.
     *
     * @param fileTree The tree. Should not be null.
     * @return The union of this tree and the given tree.
     */
    FileTree plus(FileTree fileTree);
}
