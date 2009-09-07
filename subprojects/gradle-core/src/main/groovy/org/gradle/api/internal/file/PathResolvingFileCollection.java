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

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.util.FileSet;

import java.io.File;
import java.util.*;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link FileResolver}.
 */
public class PathResolvingFileCollection extends AbstractFileCollection {
    private final List<Object> files;
    private final FileResolver resolver;

    public PathResolvingFileCollection(FileResolver resolver, Object... files) {
        this.resolver = resolver;
        this.files = new ArrayList<Object>(Arrays.asList(files));
    }

    public PathResolvingFileCollection clear() {
        files.clear();
        return this;
    }
    
    public PathResolvingFileCollection add(Object file) {
        files.add(file);
        return this;
    }

    public String getDisplayName() {
        return "file collection";
    }

    public List<?> getSources() {
        return files;
    }

    public Set<File> getFiles() {
        Set<File> result = new LinkedHashSet<File>();
        for (Object element : resolveToFilesAndFileCollections()) {
            if (element instanceof FileCollection) {
                FileCollection collection = (FileCollection) element;
                result.addAll(collection.getFiles());
            } else {
                result.add((File) element);
            }
        }
        return result;
    }

    @Override
    public FileTree getAsFileTree() {
        return new PathResolvingFileTree();
    }

    private List<Object> resolveToFilesAndFileCollections() {
        List<Object> result = new ArrayList<Object>();
        LinkedList<Object> queue = new LinkedList<Object>();
        queue.addAll(files);
        while (!queue.isEmpty()) {
            Object first = queue.removeFirst();
            if (first instanceof FileCollection) {
                result.add(first);
            } else if (first instanceof Closure) {
                Closure closure = (Closure) first;
                queue.addFirst(closure.call());
            } else if (first instanceof Collection) {
                Collection<?> collection = (Collection<?>) first;
                queue.addAll(0, collection);
            } else {
                result.add(resolver.resolve(first));
            }
        }
        return result;
    }

    private class PathResolvingFileTree extends CompositeFileTree {
        @Override
        public String getDisplayName() {
            return PathResolvingFileCollection.this.getDisplayName();
        }

        @Override
        protected Iterable<FileTree> getSourceCollections() {
            List<FileTree> trees = new ArrayList<FileTree>();
            for (Object element : resolveToFilesAndFileCollections()) {
                if (element instanceof FileCollection) {
                    FileCollection fileCollection = (FileCollection) element;
                    trees.add(fileCollection.getAsFileTree());
                } else {
                    File file = (File) element;
                    if (file.isFile()) {
                        trees.add(new FlatFileTree(file));
                    } else if (file.isDirectory()) {
                        trees.add(new FileSet(file, resolver));
                    }
                }
            }
            return trees;
        }
    }
}
