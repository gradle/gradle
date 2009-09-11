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
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDependency;

import java.io.File;
import java.util.*;

/**
 * A {@link org.gradle.api.file.FileCollection} which contains the union of the given source collections. Maintains file
 * ordering.
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

    @Override
    public TaskDependency getBuildDependencies() {
        DefaultTaskDependency dependency = new DefaultTaskDependency();
        for (FileCollection collection : getSourceCollections()) {
            dependency.add(collection);
        }
        return dependency;
    }

    /**
     * Returns a list of the leaf source collections of this collection. Flattens all composite collections.
     */
    protected List<? extends FileCollection> getSourceCollections() {
        LinkedList<FileCollection> queue = new LinkedList<FileCollection>();
        addSourceCollections(queue);
        List<FileCollection> collections = new ArrayList<FileCollection>();
        while (!queue.isEmpty()) {
            FileCollection fileCollection = queue.removeFirst();
            if (fileCollection instanceof CompositeFileCollection) {
                CompositeFileCollection compositeFileCollection = (CompositeFileCollection) fileCollection;
                compositeFileCollection.addSourceCollections(queue.subList(0, 0));
            } else {
                collections.add(fileCollection);
            }
        }
        return collections;
    }

    protected abstract void addSourceCollections(Collection<FileCollection> sources);
}
