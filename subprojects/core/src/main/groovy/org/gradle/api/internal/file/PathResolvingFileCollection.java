/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * A {@link org.gradle.api.file.FileCollection} which resolves a set of paths relative to a {@link FileResolver}.
 */
public class PathResolvingFileCollection extends CompositeFileCollection implements ConfigurableFileCollection {
    private final List<Object> files;
    private final String displayName;
    private final FileResolver resolver;
    private final DefaultTaskDependency buildDependency;

    public PathResolvingFileCollection(FileResolver fileResolver, TaskResolver taskResolver, Object... files) {
        this("file collection", fileResolver, taskResolver, files);
    }

    public PathResolvingFileCollection(String displayName, FileResolver fileResolver, TaskResolver taskResolver, Object... files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        this.files = new ArrayList<Object>(Arrays.asList(files));
        buildDependency = new DefaultTaskDependency(taskResolver);
    }

    public PathResolvingFileCollection clear() {
        files.clear();
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<?> getSources() {
        return files;
    }

    public ConfigurableFileCollection from(Object... paths) {
        for (Object path : paths) {
            files.add(path);
        }
        return this;
    }

    public ConfigurableFileCollection builtBy(Object... tasks) {
        buildDependency.add(tasks);
        return this;
    }

    public Set<Object> getBuiltBy() {
        return buildDependency.getValues();
    }

    public ConfigurableFileCollection setBuiltBy(Iterable<?> tasks) {
        buildDependency.setValues(tasks);
        return this;
    }

    @Override
    protected void addDependencies(TaskDependencyResolveContext context) {
        super.addDependencies(context);
        context.add(buildDependency);
    }

    @Override
    protected void addSourceCollections(Collection<FileCollection> sources) {
        for (Object element : resolveToFilesAndFileCollections()) {
            if (element instanceof FileCollection) {
                FileCollection collection = (FileCollection) element;
                sources.add(collection);
            } else {
                File file = (File) element;
                sources.add(new SingletonFileCollection(file, buildDependency));
            }
        }
    }

    /**
     * Converts everything in this collection which is not a FileCollection to Files, but leave FileCollections
     * unresolved.
     */
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
                Object closureResult = closure.call();
                if (closureResult != null) {
                    queue.addFirst(closureResult);
                }
            } else if (first instanceof Collection) {
                Collection<?> collection = (Collection<?>) first;
                queue.addAll(0, collection);
            } else if (first instanceof Object[]) {
                Object[] array = (Object[]) first;
                queue.addAll(0, Arrays.asList(array));
            } else if (first instanceof Callable) {
                Callable callable = (Callable) first;
                Object callableResult;
                try {
                    callableResult = callable.call();
                } catch (Exception e) {
                    throw UncheckedException.asUncheckedException(e);
                }
                if (callableResult != null) {
                    queue.add(0, callableResult);
                }
            } else {
                result.add(resolver.resolve(first));
            }
        }
        return result;
    }
}
