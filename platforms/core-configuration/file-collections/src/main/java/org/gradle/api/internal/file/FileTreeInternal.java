/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.LinksStrategy;
import org.gradle.api.tasks.util.PatternFilterable;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

public interface FileTreeInternal extends FileTree, FileCollectionInternal {
    String DEFAULT_TREE_DISPLAY_NAME = "file tree";

    void visitContentsAsFileTrees(Consumer<FileTreeInternal> visitor);

    @Override
    FileTreeInternal matching(PatternFilterable patterns);

    /**
     * Returns the contents of this tree as a flattened Set.
     *
     * <p>The order of the files in a {@code FileTree} is not stable, even on a single computer.
     *
     * @param linksStrategy The strategy to use for handling symbolic links.
     * @return The files. Returns an empty set if this tree is empty.
     * @since 8.6
     */
    default Set<File> getFiles(LinksStrategy linksStrategy) {
        return getFiles(); //no-op
    }
}
