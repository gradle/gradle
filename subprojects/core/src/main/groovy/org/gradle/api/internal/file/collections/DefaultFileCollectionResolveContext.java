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
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.GUtil;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

public class DefaultFileCollectionResolveContext implements ResolvableFileCollectionResolveContext {
    private final FileResolver fileResolver;
    private final List<Object> queue = new LinkedList<Object>();
    private List<Object> addTo = queue;
    private final Converter<? extends FileCollectionInternal> fileCollectionConverter;
    private final Converter<? extends FileTreeInternal> fileTreeConverter;

    public DefaultFileCollectionResolveContext() {
        this(new IdentityFileResolver());
    }

    public DefaultFileCollectionResolveContext(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
        fileCollectionConverter = new FileCollectionConverter();
        fileTreeConverter = new FileTreeConverter();
    }

    protected DefaultFileCollectionResolveContext(FileResolver fileResolver, Converter<? extends FileCollectionInternal> fileCollectionConverter, Converter<? extends FileTreeInternal> fileTreeConverter) {
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
    public List<FileTreeInternal> resolveAsFileTrees() {
        return doResolve(fileTreeConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileCollection} instances.
     */
    public List<FileCollectionInternal> resolveAsFileCollections() {
        Converter<? extends FileCollectionInternal> converter = fileCollectionConverter;
        return doResolve(converter);
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
            } else if (element instanceof FileCollection || element instanceof MinimalFileCollection || element instanceof MinimalFileTree) {
                converter.convertInto(element, result, fileResolver);
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
                    throw UncheckedException.asUncheckedException(e);
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

    private static class FileCollectionConverter implements Converter<FileCollectionInternal> {
        public void convertInto(Object element, Collection<? super FileCollectionInternal> result, FileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileCollections());
            } else if (element instanceof FileCollectionInternal) {
                FileCollectionInternal fileCollection = (FileCollectionInternal) element;
                result.add(fileCollection);
            } else if (element instanceof FileCollection) {
                throw new UnsupportedOperationException("Cannot convert instance of FileCollection to FileCollectionInternal");
            } else if (element instanceof MinimalFileCollection) {
                MinimalFileCollection fileCollection = (MinimalFileCollection) element;
                result.add(new FileCollectionAdapter(fileCollection));
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else {
                result.add(new FileCollectionAdapter(new ListBackedFileCollection(fileResolver.resolve(element))));
            }
        }
    }

    private static class FileTreeConverter implements Converter<FileTreeInternal> {
        public void convertInto(Object element, Collection<? super FileTreeInternal> result, FileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileTrees());
            } else if (element instanceof FileTreeInternal) {
                FileTreeInternal fileTree = (FileTreeInternal) element;
                result.add(fileTree);
            } else if (element instanceof FileTree) {
                throw new UnsupportedOperationException("Cannot convert instance of FileTree to FileTreeInternal");
            } else if (element instanceof FileCollectionInternal) {
                FileCollectionInternal fileCollection = (FileCollectionInternal) element;
                for (File file : fileCollection) {
                    convertFileToFileTree(file, result);
                }
            } else if (element instanceof FileCollection) {
                throw new UnsupportedOperationException("Cannot convert instance of FileCollection to FileTreeInternal");
            } else if (element instanceof MinimalFileCollection) {
                MinimalFileCollection fileCollection = (MinimalFileCollection) element;
                for (File file : fileCollection.getFiles()) {
                    convertFileToFileTree(file, result);
                }
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else {
                convertFileToFileTree(fileResolver.resolve(element), result);
            }
        }

        private void convertFileToFileTree(File file, Collection<? super FileTreeInternal> result) {
            if (file.isDirectory()) {
                result.add(new FileTreeAdapter(new DirectoryFileTree(file)));
            } else if (file.isFile()) {
                result.add(new FileTreeAdapter(new SingletonFileTree(file)));
            }
        }
    }
}

