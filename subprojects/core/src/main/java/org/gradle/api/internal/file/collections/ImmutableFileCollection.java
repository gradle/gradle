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
import org.gradle.internal.file.PathToFileResolver;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class ImmutableFileCollection extends AbstractFileCollection {
    private static final String DEFAULT_DISPLAY_NAME = "immutable file collection";
    private static final EmptyImmutableFileCollection EMPTY = new EmptyImmutableFileCollection();

    private final String displayName;

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
        if (Iterables.isEmpty(paths)) {
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

    private ImmutableFileCollection(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    private static class EmptyImmutableFileCollection extends ImmutableFileCollection {
        public EmptyImmutableFileCollection() {
            super(DEFAULT_DISPLAY_NAME);
        }

        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }
    }

    private static class FileOnlyImmutableFileCollection extends ImmutableFileCollection {
        private final ImmutableSet<File> files;

        public FileOnlyImmutableFileCollection(Iterable<? extends File> files) {
            super(DEFAULT_DISPLAY_NAME);
            ImmutableSet.Builder<File> filesBuilder = ImmutableSet.builder();
            if (files != null) {
                filesBuilder.addAll(files);
            }
            this.files = filesBuilder.build();
        }

        public Set<File> getFiles() {
            return files;
        }
    }

    private static class ResolvingImmutableFileCollection extends ImmutableFileCollection {
        private final FileResolver resolver;
        private final Set<Object> paths;

        public ResolvingImmutableFileCollection(FileResolver fileResolver, Iterable<?> paths) {
            super(DEFAULT_DISPLAY_NAME);
            this.resolver = fileResolver;
            ImmutableSet.Builder<Object> pathsBuilder = ImmutableSet.builder();
            if (paths != null) {
                pathsBuilder.addAll(paths);
            }
            this.paths = pathsBuilder.build();
        }

        @Override
        public Set<File> getFiles() {
            DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(resolver);
            context.add(paths);

            List<FileCollectionInternal> fileCollections = context.resolveAsFileCollections();
            List<Set<File>> fileSets = new ArrayList<Set<File>>(fileCollections.size());
            int fileCount = 0;
            for (FileCollection collection : fileCollections) {
                Set<File> files = collection.getFiles();
                fileCount += files.size();
                fileSets.add(files);
            }
            Set<File> allFiles = new LinkedHashSet<File>(fileCount);
            for (Set<File> fileSet : fileSets) {
                allFiles.addAll(fileSet);
            }
            return allFiles;
        }
    }
}
