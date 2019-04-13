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
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Set;

public abstract class ImmutableFileCollection extends AbstractFileCollection {
    private static final String DEFAULT_DISPLAY_NAME = "file collection";

    private final String displayName;

    public static ImmutableFileCollection of() {
        return EmptyImmutableFileCollection.DEFAULT;
    }

    public static FileCollectionInternal named(String displayName) {
        return new EmptyImmutableFileCollection(displayName);
    }

    public static ImmutableFileCollection of(File... files) {
        return create(DEFAULT_DISPLAY_NAME, ImmutableSet.copyOf(files));
    }

    public static FileCollectionInternal named(String displayName, File... files) {
        return create(displayName, ImmutableSet.copyOf(files));
    }

    public static ImmutableFileCollection of(Iterable<? extends File> files) {
        return create(DEFAULT_DISPLAY_NAME, ImmutableSet.copyOf(files));
    }

    public static ImmutableFileCollection named(String displayName, Iterable<? extends File> files) {
        return create(displayName, ImmutableSet.copyOf(files));
    }

    private static ImmutableFileCollection create(String displayName, ImmutableSet<File> files) {
        return files.isEmpty()
            ? EmptyImmutableFileCollection.of(displayName)
            : new FileOnlyImmutableFileCollection(displayName, files);
    }

    private ImmutableFileCollection(String displayName) {
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

    private static class FileOnlyImmutableFileCollection extends ImmutableFileCollection {
        private final ImmutableSet<File> files;

        FileOnlyImmutableFileCollection(String displayName, ImmutableSet<File> files) {
            super(displayName);
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

    private static class EmptyImmutableFileCollection extends ImmutableFileCollection {
        public static final ImmutableFileCollection DEFAULT = new EmptyImmutableFileCollection(DEFAULT_DISPLAY_NAME);

        private EmptyImmutableFileCollection(String displayName) {
            super(displayName);
        }

        public static ImmutableFileCollection of(String displayName) {
            return DEFAULT_DISPLAY_NAME.equals(displayName)
                ? DEFAULT
                : new EmptyImmutableFileCollection(displayName);
        }

        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(File file) {
            return false;
        }

        @Override
        public boolean visitContents(CancellableVisitor<File> visitor) {
            return true;
        }

        @Override
        public void visitLeafCollections(FileCollectionLeafVisitor visitor) {
        }
    }
}
