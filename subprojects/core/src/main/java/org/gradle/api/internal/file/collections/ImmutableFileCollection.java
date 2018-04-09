/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencies;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class ImmutableFileCollection extends AbstractFileCollection {
    private static final ImmutableFileCollection EMPTY = new ImmutableFileCollection() {
        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }
    };

    public static ImmutableFileCollection of(File... files) {
        return new FileOnlyImmutableFileCollection(files);
    }

    public static ImmutableFileCollection of(Object... paths) {
        return of(Arrays.asList(paths));
    }

    public static ImmutableFileCollection of(Iterable<?> paths) {
        return usingResolver(new IdentityFileResolver(), paths);
    }

    public static ImmutableFileCollection usingResolver(FileResolver fileResolver, Object[] paths) {
        return usingResolver(fileResolver, Arrays.asList(paths));
    }

    public static ImmutableFileCollection usingResolver(FileResolver fileResolver, Iterable<?> paths) {
        if (paths instanceof FileCollection) {
            return new ResolvingImmutableFileCollection(fileResolver, ImmutableSet.of(paths));
        } else if (Iterables.isEmpty(paths)) {
            return EMPTY;
        } else if (allFiles(paths)) {
            return new FileOnlyImmutableFileCollection((Iterable<? extends File>) paths);
        }
        return new ResolvingImmutableFileCollection(fileResolver, paths);
    }

    private static boolean allFiles(Iterable<?> files) {
        return Iterables.all(files, new Predicate<Object>() {
            @Override
            public boolean apply(@Nullable Object input) {
                return input instanceof File;
            }
        });
    }

    private ImmutableFileCollection() {
    }

    @Override
    public String getDisplayName() {
        return "immutable file collection";
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencies.EMPTY;
    }

    private static class FileOnlyImmutableFileCollection extends ImmutableFileCollection {
        private final ImmutableSet<File> files;

        FileOnlyImmutableFileCollection(File... files) {
            this(ImmutableSet.copyOf(files));
        }

        FileOnlyImmutableFileCollection(Iterable<? extends File> files) {
            this(ImmutableSet.copyOf(files));
        }

        private FileOnlyImmutableFileCollection(ImmutableSet<File> files) {
            this.files = files;
        }

        @Override
        public Set<File> getFiles() {
            return files;
        }
    }

    private static class ResolvingImmutableFileCollection extends ImmutableFileCollection {
        private final FileResolver resolver;
        private final Set<Object> paths;

        ResolvingImmutableFileCollection(FileResolver fileResolver, Iterable<?> paths) {
            this.resolver = fileResolver;
            this.paths = ImmutableSet.copyOf(paths);
        }

        @Override
        public Set<File> getFiles() {
            DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(resolver);
            context.add(paths);

            ImmutableSet.Builder<File> builder = ImmutableSet.builder();
            List<FileCollectionInternal> fileCollections = context.resolveAsFileCollections();
            for (FileCollection collection : fileCollections) {
                builder.addAll(collection.getFiles());
            }
            return builder.build();
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return new AbstractTaskDependency() {

                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    BuildDependenciesOnlyFileCollectionResolveContext fileContext = new BuildDependenciesOnlyFileCollectionResolveContext(context);
                    fileContext.add(paths);
                }
            };
        }
    }
}
