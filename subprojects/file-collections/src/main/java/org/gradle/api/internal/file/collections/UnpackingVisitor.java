/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.internal.file.AbstractOpaqueFileCollection;
import org.gradle.api.internal.file.CompositeFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.DeferredUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

public class UnpackingVisitor {
    private final Consumer<FileCollectionInternal> visitor;
    private final PathToFileResolver resolver;
    private final Factory<PatternSet> patternSetFactory;
    private final boolean includeBuildable;

    public UnpackingVisitor(Consumer<FileCollectionInternal> visitor, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory) {
        this(visitor, resolver, patternSetFactory, true);
    }

    public UnpackingVisitor(Consumer<FileCollectionInternal> visitor, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory, boolean includeBuildable) {
        this.visitor = visitor;
        this.resolver = resolver;
        this.patternSetFactory = patternSetFactory;
        this.includeBuildable = includeBuildable;
    }

    public void add(@Nullable Object element) {
        if (element instanceof FileCollectionInternal) {
            // FileCollection is-a Iterable, Buildable and TaskDependencyContainer, so check before checking for these things
            visitor.accept((FileCollectionInternal) element);
            return;
        }
        if (element instanceof DirectoryTree) {
            visitor.accept(new FileTreeAdapter((MinimalFileTree) element, patternSetFactory));
            return;
        }
        if (element instanceof ProviderInternal) {
            // ProviderInternal is-a TaskDependencyContainer, so check first
            ProviderInternal<?> provider = (ProviderInternal<?>) element;
            visitor.accept(new ProviderBackedFileCollection(provider, resolver, patternSetFactory));
            return;
        }
        if (includeBuildable && (element instanceof Buildable || element instanceof TaskDependencyContainer)) {
            visitor.accept(new BuildableElementFileCollection(element, resolver, patternSetFactory));
            return;
        }

        if (element instanceof Task) {
            visitor.accept((FileCollectionInternal) ((Task) element).getOutputs().getFiles());
        } else if (element instanceof TaskOutputs) {
            visitor.accept((FileCollectionInternal) ((TaskOutputs) element).getFiles());
        } else if (DeferredUtil.isNestableDeferred(element)) {
            Object deferredResult = DeferredUtil.unpackNestableDeferred(element);
            if (deferredResult != null) {
                add(deferredResult);
            }
        } else if (element instanceof Path) {
            // Path is-a Iterable, so check before checking for Iterable
            visitSingleFile(element);
        } else if (element instanceof Iterable) {
            Iterable<?> iterable = (Iterable) element;
            for (Object item : iterable) {
                add(item);
            }
        } else if (element instanceof Object[]) {
            Object[] array = (Object[]) element;
            for (Object value : array) {
                add(value);
            }
        } else if (element != null) {
            // Treat everything else as a single file
            visitSingleFile(element);
        }
    }

    private void visitSingleFile(Object element) {
        visitor.accept(new SingleFileResolvingFileCollection(element, resolver, patternSetFactory));
    }

    private static class SingleFileResolvingFileCollection extends AbstractOpaqueFileCollection {
        private Object element;
        private final PathToFileResolver resolver;
        private File resolved;

        public SingleFileResolvingFileCollection(Object element, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory) {
            super(patternSetFactory);
            this.element = element;
            this.resolver = resolver;
        }

        @Override
        public String getDisplayName() {
            return "file collection";
        }

        @Override
        protected Set<File> getIntrinsicFiles() {
            if (resolved == null) {
                resolved = resolver.resolve(element);
                element = null;
            }
            return ImmutableSet.of(resolved);
        }
    }

    private static class BuildableElementFileCollection extends CompositeFileCollection {
        private final Object element;
        private final PathToFileResolver resolver;
        private final Factory<PatternSet> patternSetFactory;

        public BuildableElementFileCollection(Object element, PathToFileResolver resolver, Factory<PatternSet> patternSetFactory) {
            this.element = element;
            this.resolver = resolver;
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(element);
        }

        @Override
        public String getDisplayName() {
            return "file collection";
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            new UnpackingVisitor(visitor, resolver, patternSetFactory, false).add(element);
        }
    }
}
