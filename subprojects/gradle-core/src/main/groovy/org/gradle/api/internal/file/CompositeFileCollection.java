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
package org.gradle.api.internal.file;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.StopExecutionException;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link org.gradle.api.file.FileCollection} which contains the union of the given source collections. Maintains
 * file ordering.
 */
public abstract class CompositeFileCollection extends AbstractFileCollection {
    public Set<File> getFiles() {
        Set<File> files = new LinkedHashSet<File>();
        for (FileCollection collection : getSourceCollections()) {
            files.addAll(collection.getFiles());
        }
        return files;
    }

    @Override
    public FileCollection stopExecutionIfEmpty() throws StopExecutionException {
        for (FileCollection collection : getSourceCollections()) {
            try {
                collection.stopExecutionIfEmpty();
                return this;
            } catch (StopExecutionException e) {
                // Continue
            }
        }
        throw new StopExecutionException(String.format("No files found in %s.", getDisplayName()));
    }
    
    @Override
    public Object addToAntBuilder(Object node, String childNodeName) {
        for (FileCollection fileCollection : getSourceCollections()) {
            fileCollection.addToAntBuilder(node, childNodeName);
        }
        return this;
    }

    @Override
    public FileTree getAsFileTree() {
        UnionFileTree tree = new UnionFileTree(getDisplayName());
        for (FileCollection collection : getSourceCollections()) {
            tree.add(collection.getAsFileTree());
        }
        return tree;
    }

    protected abstract Iterable<? extends FileCollection> getSourceCollections();
}
