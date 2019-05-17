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

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.util.DeferredUtil;
import org.gradle.util.GUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DefaultFileCollectionResolveContext implements ResolvableFileCollectionResolveContext {
    protected final PathToFileResolver fileResolver;
    private final List<Object> queue = new LinkedList<Object>();
    private List<Object> addTo = queue;
    private final Converter<? extends FileCollectionInternal> fileCollectionConverter;
    private final Converter<? extends FileTreeInternal> fileTreeConverter;

    public DefaultFileCollectionResolveContext(FileResolver fileResolver) {
        this(fileResolver, new FileCollectionConverter(fileResolver.getPatternSetFactory()), new FileTreeConverter(fileResolver.getPatternSetFactory()));
    }

    private DefaultFileCollectionResolveContext(PathToFileResolver fileResolver, Converter<? extends FileCollectionInternal> fileCollectionConverter, Converter<? extends FileTreeInternal> fileTreeConverter) {
        this.fileResolver = fileResolver;
        this.fileCollectionConverter = fileCollectionConverter;
        this.fileTreeConverter = fileTreeConverter;
    }

    @Override
    public FileCollectionResolveContext add(Object element) {
        addTo.add(element);
        return this;
    }

    @Override
    public FileCollectionResolveContext push(PathToFileResolver fileResolver) {
        ResolvableFileCollectionResolveContext nestedContext = newContext(fileResolver);
        add(nestedContext);
        return nestedContext;
    }

    protected ResolvableFileCollectionResolveContext newContext(PathToFileResolver fileResolver) {
        return new DefaultFileCollectionResolveContext(fileResolver, fileCollectionConverter, fileTreeConverter);
    }

    @Override
    public final ResolvableFileCollectionResolveContext newContext() {
        return newContext(fileResolver);
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
            // TODO - need to sync with BuildDependenciesOnlyFileCollectionResolveContext
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                converter.convertInto(nestedContext, result, fileResolver);
            } else if (element instanceof FileCollectionContainer) {
                FileCollectionContainer fileCollection = (FileCollectionContainer) element;
                resolveNested(fileCollection, result, converter);
            } else if (element instanceof FileCollection || element instanceof MinimalFileCollection) {
                converter.convertInto(element, result, fileResolver);
            } else if (element instanceof Task) {
                Task task = (Task) element;
                queue.add(0, task.getOutputs().getFiles());
            } else if (element instanceof TaskOutputs) {
                TaskOutputs outputs = (TaskOutputs) element;
                queue.add(0, outputs.getFiles());
            } else if (DeferredUtil.isDeferred(element)) {
                Object deferredResult = DeferredUtil.unpack(element);
                if (deferredResult != null) {
                    queue.add(0, deferredResult);
                }
            } else if (element instanceof Path) {
                queue.add(0, ((Path) element).toFile());
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

    protected <T> void resolveNested(FileCollectionContainer fileCollection, List<T> result, Converter<? extends T> converter) {
        addTo = queue.subList(0, 0);
        try {
            fileCollection.visitContents(this);
        } finally {
            addTo = queue;
        }
    }

    protected interface Converter<T> {
        void convertInto(Object element, Collection<? super T> result, PathToFileResolver resolver);
    }

    public static class FileCollectionConverter implements Converter<FileCollectionInternal> {
        private final Factory<PatternSet> patternSetFactory;

        public FileCollectionConverter(Factory<PatternSet> patternSetFactory) {
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void convertInto(Object element, Collection<? super FileCollectionInternal> result, PathToFileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileCollections());
            } else if (element instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) element;
                result.add(Cast.cast(FileCollectionInternal.class, fileCollection));
            } else if (element instanceof MinimalFileTree) {
                MinimalFileTree fileTree = (MinimalFileTree) element;
                result.add(new FileTreeAdapter(fileTree, patternSetFactory));
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

    public static class FileTreeConverter implements Converter<FileTreeInternal> {
        private final Factory<PatternSet> patternSetFactory;

        public FileTreeConverter(Factory<PatternSet> patternSetFactory) {
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void convertInto(Object element, Collection<? super FileTreeInternal> result, PathToFileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                DefaultFileCollectionResolveContext nestedContext = (DefaultFileCollectionResolveContext) element;
                result.addAll(nestedContext.resolveAsFileTrees());
            } else if (element instanceof FileTree) {
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
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to FileTree", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return;
            } else {
                convertFileToFileTree(fileResolver.resolve(element), result);
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

    public static class MinimalFileCollectionConverter implements Converter<MinimalFileCollection> {
        @Override
        public void convertInto(Object element, Collection<? super MinimalFileCollection> result, PathToFileResolver resolver) {
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

