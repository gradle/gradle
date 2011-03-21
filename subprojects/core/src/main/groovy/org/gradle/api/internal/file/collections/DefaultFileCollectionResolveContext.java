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

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.SingletonFileCollection;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.GUtil;
import org.gradle.util.UncheckedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class DefaultFileCollectionResolveContext implements FileCollectionResolveContext {
    private final FileResolver fileResolver;
    private final TaskDependency defaultBuiltBy;
    private final LinkedList<Object> queue = new LinkedList<Object>();
    private List<Object> addTo = queue;

    public DefaultFileCollectionResolveContext(FileResolver fileResolver, TaskDependency defaultBuiltBy) {
        this.fileResolver = fileResolver;
        this.defaultBuiltBy = defaultBuiltBy;
    }

    public void add(Object element) {
        addTo.add(element);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileCollection} instances.
     */
    public List<FileCollection> resolve() {
        List<FileCollection> result = new ArrayList<FileCollection>();
        while (!queue.isEmpty()) {
            Object element = queue.removeFirst();
            if (element instanceof CompositeFileCollection) {
                CompositeFileCollection fileCollection = (CompositeFileCollection) element;
                addTo = queue.subList(0, 0);
                try {
                    fileCollection.resolve(this);
                } finally {
                    addTo = queue;
                }
            } else if (element instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) element;
                result.add(fileCollection);
            } else if (element instanceof MinimalFileCollection) {
                MinimalFileCollection fileCollection = (MinimalFileCollection) element;
                result.add(new FileCollectionAdapter(fileCollection));
            } else if (element instanceof Closure) {
                Closure closure = (Closure) element;
                Object closureResult = closure.call();
                if (closureResult != null) {
                    queue.addFirst(closureResult);
                }
            } else if (element instanceof Callable) {
                Callable callable = (Callable) element;
                Object callableResult;
                try {
                    callableResult = callable.call();
                } catch (Exception e) {
                    throw UncheckedException.asUncheckedException(e);
                }
                if (callableResult != null) {
                    queue.addFirst(callableResult);
                }
            } else if (element instanceof Iterable) {
                Iterable<?> iterable = (Iterable) element;
                GUtil.addToCollection(queue.subList(0, 0), iterable);
            } else if (element instanceof Object[]) {
                Object[] array = (Object[]) element;
                GUtil.addToCollection(queue.subList(0, 0), Arrays.asList(array));
            } else {
                result.add(new SingletonFileCollection(fileResolver.resolve(element), defaultBuiltBy));
            }
        }
        return result;
    }
}

