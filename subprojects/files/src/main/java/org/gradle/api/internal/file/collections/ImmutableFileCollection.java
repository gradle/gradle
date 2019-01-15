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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.List;
import java.util.Set;

public abstract class ImmutableFileCollection extends AbstractFileCollection {
    private static final ImmutableFileCollection EMPTY = new ImmutableFileCollection() {
        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }
    };

    public static ImmutableFileCollection of() {
        return EMPTY;
    }

    public static ImmutableFileCollection of(File... files) {
        if (files.length == 0) {
            return EMPTY;
        }
        return new FileOnlyImmutableFileCollection(ImmutableSet.copyOf(files));
    }

    public static ImmutableFileCollection of(Iterable<? extends File> files) {
        if (Iterables.isEmpty(files)) {
            return EMPTY;
        }
        return new FileOnlyImmutableFileCollection(ImmutableSet.copyOf(files));
    }

    public static ImmutableFileCollection usingResolver(FileResolver fileResolver, Object... paths) {
        if (paths.length == 0) {
            return EMPTY;
        }
        return new ResolvingImmutableFileCollection(fileResolver, paths);
    }

    private ImmutableFileCollection() {
    }

    @Override
    public String getDisplayName() {
        return "file collection";
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
    }

    private static class FileOnlyImmutableFileCollection extends ImmutableFileCollection {
        private final ImmutableSet<File> files;

        FileOnlyImmutableFileCollection(ImmutableSet<File> files) {
            this.files = files;
        }

        @Override
        public Set<File> getFiles() {
            return files;
        }


        @Override
        public String toString() {
            if (files.size() == 1) {
                return String.format("file '%s'", files.iterator().next().getAbsolutePath());
            }

            return super.toString();
        }
    }

    private static class ResolvingImmutableFileCollection extends ImmutableFileCollection {
        private final FileResolver resolver;
        private final Set<Object> paths;

        ResolvingImmutableFileCollection(FileResolver fileResolver, Object... paths) {
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
