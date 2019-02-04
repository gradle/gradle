/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Buildable;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.file.collections.FileCollectionAdapter;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.file.PathToFileResolver;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultFileCollectionFactory implements FileCollectionFactory {
    public static final String DEFAULT_DISPLAY_NAME = "file collection";
    private final PathToFileResolver fileResolver;
    @Nullable
    private final TaskResolver taskResolver;

    // Used by the Kotlin-dsl base plugin
    // TODO - remove this
    @Deprecated
    public DefaultFileCollectionFactory() {
        this(new IdentityFileResolver(), null);
    }

    public DefaultFileCollectionFactory(PathToFileResolver fileResolver, @Nullable TaskResolver taskResolver) {
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
    }

    @Override
    public ConfigurableFileCollection configurableFiles() {
        return new DefaultConfigurableFileCollection(fileResolver, taskResolver);
    }

    @Override
    public ConfigurableFileCollection configurableFiles(String displayName) {
        return new DefaultConfigurableFileCollection(displayName, fileResolver, taskResolver);
    }

    @Override
    public FileCollectionInternal create(final TaskDependency builtBy, MinimalFileSet contents) {
        if (contents instanceof Buildable) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }
        return new FileCollectionAdapter(contents) {
            @Override
            public TaskDependency getBuildDependencies() {
                return builtBy;
            }
        };
    }

    @Override
    public FileCollectionInternal create(MinimalFileSet contents) {
        return new FileCollectionAdapter(contents);
    }

    @Override
    public FileCollectionInternal resolving(String displayName, List<?> files) {
        if (files.isEmpty()) {
            return new EmptyFileCollection(displayName);
        }
        return new ResolvingFileCollection(displayName, fileResolver, ImmutableList.copyOf(files));
    }

    @Override
    public FileCollectionInternal resolving(Object... files) {
        return resolving(DEFAULT_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal resolving(String displayName, Object... files) {
        return resolving(displayName, ImmutableList.copyOf(files));
    }

    @Override
    public FileCollectionInternal empty(String displayName) {
        return new EmptyFileCollection(displayName);
    }

    @Override
    public FileCollectionInternal empty() {
        return empty(DEFAULT_DISPLAY_NAME);
    }

    @Override
    public FileCollectionInternal fixed(File... files) {
        return fixed(DEFAULT_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal fixed(final String displayName, File... files) {
        if (files.length == 0) {
            return new EmptyFileCollection(displayName);
        }
        return new FixedFileCollection(displayName, ImmutableSet.copyOf(files));
    }

    @Override
    public FileCollectionInternal fixed(Collection<File> files) {
        return fixed(DEFAULT_DISPLAY_NAME, files);
    }

    @Override
    public FileCollectionInternal fixed(final String displayName, Collection<File> files) {
        if (files.isEmpty()) {
            return new EmptyFileCollection(displayName);
        }
        return new FixedFileCollection(displayName, ImmutableSet.copyOf(files));
    }

    private static final class EmptyFileCollection extends AbstractFileCollection {
        private final String displayName;

        public EmptyFileCollection(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return TaskDependencyInternal.EMPTY;
        }

        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }

        @Override
        public void visitLeafCollections(FileCollectionLeafVisitor visitor) {
        }
    }

    private static final class FixedFileCollection extends AbstractFileCollection {
        private final String displayName;
        private final ImmutableSet<File> files;

        public FixedFileCollection(String displayName, ImmutableSet<File> files) {
            this.displayName = displayName;
            this.files = files;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public Set<File> getFiles() {
            return files;
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return TaskDependencyInternal.EMPTY;
        }
    }

    private static final class ResolvingFileCollection extends CompositeFileCollection {
        private final String displayName;
        private final PathToFileResolver resolver;
        private final ImmutableList<Object> paths;

        public ResolvingFileCollection(String displayName, PathToFileResolver resolver, ImmutableList<Object> paths) {
            this.displayName = displayName;
            this.resolver = resolver;
            this.paths = paths;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            FileCollectionResolveContext nested = context.push(resolver);
            nested.add(paths);
        }
    }
}
