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

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.FileSet;

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
    public boolean contains(File file) {
        for (FileCollection collection : getSourceCollections()) {
            if (collection.contains(file)) {
                return true;
            }
        }
        return false;
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
    protected void addAsResourceCollection(Object builder, String nodeName) {
        for (FileCollection fileCollection : getSourceCollections()) {
            fileCollection.addToAntBuilder(builder, nodeName, AntType.ResourceCollection);
        }
    }

    @Override
    protected Collection<FileSet> getAsFileSets() {
        List<FileSet> fileSets = new ArrayList<FileSet>();
        for (FileCollection source : getSourceCollections()) {
            AbstractFileCollection collection = (AbstractFileCollection) source;
            fileSets.addAll(collection.getAsFileSets());
        }
        return fileSets;
    }

    @Override
    public FileTree getAsFileTree() {
        return new CompositeFileTree() {
            @Override
            protected void addSourceCollections(Collection<FileCollection> sources) {
                for (FileCollection collection : CompositeFileCollection.this.getSourceCollections()) {
                    sources.add(collection.getAsFileTree());
                }
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }

            @Override
            public TaskDependency getBuildDependencies() {
                return CompositeFileCollection.this.getBuildDependencies();
            }
        };
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency dependency = new DefaultTaskDependency();
                addDependencies(dependency);
                return dependency.getDependencies(task);
            }
        };
    }

    /**
     * Allows subclasses to add additional dependencies
     * @param dependency The dependency container to add dependencies to.
     */
    protected void addDependencies(DefaultTaskDependency dependency) {
        for (FileCollection collection : getSourceCollections()) {
            dependency.add(collection);
        }
    }

    protected List<? extends FileCollection> getSourceCollections() {
        List<FileCollection> collections = new ArrayList<FileCollection>();
        addSourceCollections(collections);
        return collections;
    }

    protected abstract void addSourceCollections(Collection<FileCollection> sources);
}
