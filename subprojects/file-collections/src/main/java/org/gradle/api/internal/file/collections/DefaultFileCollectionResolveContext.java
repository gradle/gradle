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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DefaultFileCollectionResolveContext implements ResolvableFileCollectionResolveContext {
    private final List<Object> queue = new LinkedList<Object>();
    private final Converter<? extends FileCollectionInternal> fileCollectionConverter;
    private final Converter<? extends FileTreeInternal> fileTreeConverter;

    public DefaultFileCollectionResolveContext(Factory<PatternSet> patternSetFactory) {
        this(new FileCollectionConverter(patternSetFactory), new FileTreeConverter(patternSetFactory));
    }

    private DefaultFileCollectionResolveContext(Converter<? extends FileCollectionInternal> fileCollectionConverter, Converter<? extends FileTreeInternal> fileTreeConverter) {
        this.fileCollectionConverter = fileCollectionConverter;
        this.fileTreeConverter = fileTreeConverter;
    }

    @Override
    public FileCollectionResolveContext addAll(Iterable<?> elements) {
        for (Object element : elements) {
            add(element);
        }
        return this;
    }

    @Override
    public FileCollectionResolveContext add(Object element) {
        if (element instanceof FileCollectionContainer) {
            FileCollectionContainer container = (FileCollectionContainer) element;
            container.visitContents(this);
        } else {
            queue.add(element);
        }
        return this;
    }

    @Override
    public boolean maybeAdd(Object element) {
        return false;
    }

    @Override
    public FileCollectionResolveContext add(Object element, PathToFileResolver resolver) {
        queue.add(resolver.resolve(element));
        return this;
    }

    @Override
    public final ResolvableFileCollectionResolveContext newContext() {
        return new DefaultFileCollectionResolveContext(fileCollectionConverter, fileTreeConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileTree} instances.
     */
    @Override
    public List<FileTreeInternal> resolveAsFileTrees() {
        return doResolve(fileTreeConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileCollection} instances.
     */
    @Override
    public List<FileCollectionInternal> resolveAsFileCollections() {
        return doResolve(fileCollectionConverter);
    }

    private <T> List<T> doResolve(Converter<? extends T> converter) {
        List<T> result = new ArrayList<T>();
        for (Object element : queue) {
            converter.convertInto(element, result);
        }
        return result;
    }

    protected interface Converter<T> {
        void convertInto(Object element, Collection<? super T> result);
    }

    public static class FileCollectionConverter implements Converter<FileCollectionInternal> {
        private final Factory<PatternSet> patternSetFactory;

        public FileCollectionConverter(Factory<PatternSet> patternSetFactory) {
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void convertInto(Object element, Collection<? super FileCollectionInternal> result) {
            if (element instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) element;
                result.add(Cast.cast(FileCollectionInternal.class, fileCollection));
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree, patternSetFactory));
            } else if (element instanceof MinimalFileSet) {
                MinimalFileSet fileSet = (MinimalFileSet) element;
                result.add(new FileCollectionAdapter(fileSet));
            } else if (element instanceof MinimalFileCollection) {
                throw new IllegalArgumentException(String.format("Cannot convert instance of %s to FileCollection", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else if (element instanceof File) {
                result.add(new FileCollectionAdapter(new ListBackedFileSet((File) element)));
            } else {
                throw new IllegalArgumentException("Don't know how to convert element into a FileCollection: " + element);
            }
        }
    }

    public static class FileTreeConverter implements Converter<FileTreeInternal> {
        private final Factory<PatternSet> patternSetFactory;

        public FileTreeConverter(Factory<PatternSet> patternSetFactory) {
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void convertInto(Object element, Collection<? super FileTreeInternal> result) {
            if (element instanceof FileTree) {
                FileTree fileTree = (FileTree) element;
                result.add(Cast.cast(FileTreeInternal.class, fileTree));
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree, patternSetFactory));
            } else if (element instanceof MinimalFileSet) {
                MinimalFileSet fileSet = (MinimalFileSet) element;
                for (File file : fileSet.getFiles()) {
                    convertFileToFileTree(file, result);
                }
            } else if (element instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) element;
                for (File file : fileCollection) {
                    convertFileToFileTree(file, result);
                }
            } else if (element instanceof MinimalFileCollection) {
                throw new IllegalArgumentException(String.format("Cannot convert instance of %s to FileTree", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else if (element instanceof File) {
                convertFileToFileTree((File) element, result);
            } else {
                throw new IllegalArgumentException("Don't know how to convert element into a FileTree: " + element);
            }
        }

        private void convertFileToFileTree(File file, Collection<? super FileTreeInternal> result) {
            if (file.isDirectory()) {
                result.add(new FileTreeAdapter(new DirectoryFileTree(file, patternSetFactory.create(), FileSystems.getDefault()), patternSetFactory));
            } else if (file.isFile()) {
                result.add(new FileTreeAdapter(new DefaultSingletonFileTree(file), patternSetFactory));
            }
        }
    }
}

