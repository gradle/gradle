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
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

public class DefaultFileCollectionResolveContext implements ResolvableFileCollectionResolveContext {
    private final FileResolver fileResolver;
    private final List<Object> queue = new LinkedList<Object>();
    private List<Object> addTo = queue;
    private final Converter<? extends FileCollection> fileCollectionConverter;
    private final Converter<? extends FileTree> fileTreeConverter;

    public DefaultFileCollectionResolveContext() {
        this(new IdentityFileResolver());
    }

    public DefaultFileCollectionResolveContext(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
        fileCollectionConverter = new FileCollectionConverter();
        fileTreeConverter = new FileTreeConverter();
    }

    protected DefaultFileCollectionResolveContext(FileResolver fileResolver, Converter<? extends FileCollection> fileCollectionConverter, Converter<? extends FileTree> fileTreeConverter) {
        this.fileResolver = fileResolver;
        this.fileCollectionConverter = fileCollectionConverter;
        this.fileTreeConverter = fileTreeConverter;
    }

    public DefaultFileCollectionResolveContext add(Object element) {
        addTo.add(element);
        return this;
    }

    public DefaultFileCollectionResolveContext push(FileResolver fileResolver) {
        DefaultFileCollectionResolveContext nestedContext = new DefaultFileCollectionResolveContext(fileResolver, fileCollectionConverter, fileTreeConverter);
        add(nestedContext);
        return nestedContext;
    }

    public ResolvableFileCollectionResolveContext newContext() {
        return new DefaultFileCollectionResolveContext(fileResolver, fileCollectionConverter, fileTreeConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileTree} instances.
     */
    public List<FileTree> resolveAsFileTrees() {
        return doResolve(fileTreeConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileCollection} instances.
     */
    public List<FileCollection> resolveAsFileCollections() {
        return doResolve(fileCollectionConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link MinimalFileCollection} instances.
     */
    public List<MinimalFileCollection> resolveAsMinimalFileCollections() {
        return doResolve(new MinimalFileCollectionConverter());
    }

    private <T> List<T> doResolve(Converter<? extends T> converter) {
        List<T> result = new ArrayList<T>();
        while (!queue.isEmpty()) {
            Object element = queue.remove(0);
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                converter.convertInto(nestedContext, result, fileResolver);
            } else if (element instanceof FileCollectionContainer) {
                FileCollectionContainer fileCollection = (FileCollectionContainer) element;
                resolveNested(fileCollection);
            } else if (element instanceof FileCollection || element instanceof MinimalFileCollection) {
                converter.convertInto(element, result, fileResolver);
            } else if (element instanceof Task) {
                Task task = (Task) element;
                queue.add(0, task.getOutputs().getFiles());
            } else if (element instanceof TaskOutputs) {
                TaskOutputs outputs = (TaskOutputs) element;
                queue.add(0, outputs.getFiles());
            } else if (element instanceof Closure) {
                Closure closure = (Closure) element;
                Object closureResult = closure.call();
                if (closureResult != null) {
                    queue.add(0, closureResult);
                }
            } else if (element instanceof Callable) {
                Callable callable = (Callable) element;
                Object callableResult;
                try {
                    callableResult = callable.call();
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (callableResult != null) {
                    queue.add(0, callableResult);
                }
            } else if (element instanceof Iterable) {
                Iterable<?> iterable = (Iterable) element;
                GUtil.addToCollection(queue.subList(0, 0), iterable);
            } else if (element instanceof Object[]) {
                Object[] array = (Object[]) element;
                GUtil.addToCollection(queue.subList(0, 0), Arrays.asList(array));
            } else {
                converter.convertInto(element, result, fileResolver);
            }
        }
        return result;
    }

    private void resolveNested(FileCollectionContainer fileCollection) {
        addTo = queue.subList(0, 0);
        try {
            fileCollection.resolve(this);
        } finally {
            addTo = queue;
        }
    }

    protected interface Converter<T> {
        void convertInto(Object element, Collection<? super T> result, FileResolver resolver);
    }

    private static class FileCollectionConverter implements Converter<FileCollection> {
        public void convertInto(Object element, Collection<? super FileCollection> result, FileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileCollections());
            } else if (element instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) element;
                result.add(fileCollection);
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree));
            } else if (element instanceof MinimalFileSet) {
                MinimalFileSet fileSet = (MinimalFileSet) element;
                result.add(new FileCollectionAdapter(fileSet));
            } else if (element instanceof MinimalFileCollection) {
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to FileTree", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else {
                result.add(new FileCollectionAdapter(new ListBackedFileSet(fileResolver.resolve(element))));
            }
        }
    }

    private static class FileTreeConverter implements Converter<FileTree> {
        public void convertInto(Object element, Collection<? super FileTree> result, FileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileTrees());
            } else if (element instanceof FileTree) {
                FileTree fileTree = (FileTree) element;
                result.add(fileTree);
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree));
            } else if (element instanceof MinimalFileSet) {
                MinimalFileSet fileSet = (MinimalFileSet) element;
                for (File file : fileSet.getFiles()) {
                    convertFileToFileTree(file, result);
                }
            } else if (element instanceof FileCollection || element instanceof MinimalFileCollection) {
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to FileTree", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else {
                convertFileToFileTree(fileResolver.resolve(element), result);
            }
        }

        private void convertFileToFileTree(File file, Collection<? super FileTree> result) {
            if (file.isDirectory()) {
                result.add(new FileTreeAdapter(new DirectoryFileTree(file)));
            } else if (file.isFile()) {
                result.add(new FileTreeAdapter(new SingletonFileTree(file)));
            }
        }
    }

    private static class MinimalFileCollectionConverter implements Converter<MinimalFileCollection> {
        public void convertInto(Object element, Collection<? super MinimalFileCollection> result, FileResolver resolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsMinimalFileCollections());
            } else if (element instanceof MinimalFileCollection) {
                MinimalFileCollection collection = (MinimalFileCollection) element;
                result.add(collection);
            } else if (element instanceof FileCollection) {
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to MinimalFileCollection", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else {
                result.add(new ListBackedFileSet(resolver.resolve(element)));
            }
        }
    }
}

