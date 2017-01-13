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
import org.gradle.api.Action;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.HasInternalProtocol;

import java.io.File;
import java.util.Set;

/**
 * <p>A {@code FileTree} represents a hierarchy of files. It extends {@link FileCollection} to add hierarchy query and
 * manipulation methods. You typically use a {@code FileTree} to represent files to copy or the contents of an
 * archive.</p>
 *
 * <p>You can obtain a {@code FileTree} instance using {@link org.gradle.api.Project#fileTree(java.util.Map)},
 * {@link org.gradle.api.Project#zipTree(Object)} or {@link org.gradle.api.Project#tarTree(Object)}.
 * </p>
 */
@HasInternalProtocol
public interface FileTree extends FileCollection {
    /**
     * <p>Restricts the contents of this tree to those files matching the given filter. The filtered tree is live, so
     * that any changes to this tree are reflected in the filtered tree.</p>
     *
     * <p>The given closure is used to configure the filter. A {@link org.gradle.api.tasks.util.PatternFilterable} is
     * passed to the closure as its delegate. Only files which match the specified include patterns will be included in
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
     * <p>The given action is used to configure the filter. A {@link org.gradle.api.tasks.util.PatternFilterable} is
     * passed to the action. Only files which match the specified include patterns will be included in
     * the filtered tree. Any files which match the specified exclude patterns will be excluded from the filtered
     * tree.</p>
     *
     * @param filterConfigAction Action to use to configure the filter.
     * @return The filtered tree.
     */
    FileTree matching(Action<? super PatternFilterable> filterConfigAction);

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
    FileTree matching(PatternFilterable patterns);

    /**
     * Visits the files and directories in this file tree. Files are visited in depth-first prefix order, so that a directory
     * is visited before its children.
     *
     * @param visitor The visitor.
     * @return this
     */
    FileTree visit(FileVisitor visitor);

    /**
     * Visits the files and directories in this file tree. Files are visited in depth-first prefix order, so that a directory
     * is visited before its children. The file/directory to be visited is passed to the given closure as a {@link
     * FileVisitDetails}
     *
     * @param visitor The visitor.
     * @return this
     */
    FileTree visit(Closure visitor);

    /**
     * Visits the files and directories in this file tree. Files are visited in depth-first prefix order, so that a directory
     * is visited before its children. The file/directory to be visited is passed to the given action as a {@link
     * FileVisitDetails}
     *
     * @param visitor The visitor.
     * @return this
     */
    FileTree visit(Action<? super FileVisitDetails> visitor);

    /**
     * Returns a {@code FileTree} which contains the union of this tree and the given tree. The returned tree is live,
     * so that changes to either this tree or the other source tree are reflected in the returned tree.
     *
     * @param fileTree The tree. Should not be null.
     * @return The union of this tree and the given tree.
     */
    FileTree plus(FileTree fileTree);

    /**
     * Returns this.
     *
     * @return this
     */
    FileTree getAsFileTree();

    /**
     * Returns the contents of this tree as a flattened Set.
     *
     * @return The files. Returns an empty set if this tree is empty.
     */
    Set<File> getFiles();
}
