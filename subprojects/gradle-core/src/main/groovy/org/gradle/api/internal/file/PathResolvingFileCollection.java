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
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link FileResolver}.
 */
public class PathResolvingFileCollection extends CompositeFileCollection {
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

    @Override
    protected void addSourceCollections(Collection<FileCollection> sources) {
        for (final Object element : resolveToFilesAndFileCollections()) {
            if (element instanceof FileCollection) {
                FileCollection collection = (FileCollection) element;
                sources.add(collection);
            } else {
                final File file = (File) element;
                sources.add(new SingletonFileCollection(file));
            }
        }
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
            } else if (first instanceof Callable) {
                Callable callable = (Callable) first;
                try {
                    queue.add(0, callable.call());
                } catch (Exception e) {
                    throw new GradleException(e);
                }
            } else {
                result.add(resolver.resolve(first));
            }
        }
        return result;
    }

    private static class SingletonFileCollection extends AbstractFileCollection {
        private final File file;

        public SingletonFileCollection(File file) {
            this.file = file;
        }

        @Override
        public String getDisplayName() {
            return "file collection";
        }

        public Set<File> getFiles() {
            return Collections.singleton(file);
        }
    }
}
