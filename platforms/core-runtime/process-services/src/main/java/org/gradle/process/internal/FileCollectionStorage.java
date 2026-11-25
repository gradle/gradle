/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Where a file collection option keeps its files: a collection the options create on first use,
 * or a collection a task owns once it moves the option there, see {@link OptionStorage}.
 * The callers decide which current files become the convention of the task's collection.
 */
@NullMarked
abstract class FileCollectionStorage {

    abstract ConfigurableFileCollection get();

    @Nullable
    abstract ConfigurableFileCollection getIfCreated();

    /**
     * Sets the files. Options that own the collection start a new one, so the old collection can be one of the sources.
     */
    abstract void replace(FileCollection files);

    static FileCollectionStorage createdOnUse(FileCollectionFactory factory, String displayName) {
        return new CreatedOnUse(factory, displayName);
    }

    static FileCollectionStorage in(ConfigurableFileCollection files) {
        return new Owned(files);
    }

    private static final class CreatedOnUse extends FileCollectionStorage {
        private final FileCollectionFactory factory;
        private final String displayName;
        @Nullable
        private ConfigurableFileCollection files;

        CreatedOnUse(FileCollectionFactory factory, String displayName) {
            this.factory = factory;
            this.displayName = displayName;
        }

        @Override
        ConfigurableFileCollection get() {
            ConfigurableFileCollection current = files;
            if (current == null) {
                current = create();
            }
            return current;
        }

        @Override
        @Nullable
        ConfigurableFileCollection getIfCreated() {
            return files;
        }

        @Override
        void replace(FileCollection files) {
            create().setFrom(files);
        }

        private ConfigurableFileCollection create() {
            ConfigurableFileCollection created = factory.configurableFiles(displayName);
            files = created;
            return created;
        }
    }

    private static final class Owned extends FileCollectionStorage {
        private final ConfigurableFileCollection files;

        Owned(ConfigurableFileCollection files) {
            this.files = files;
        }

        @Override
        ConfigurableFileCollection get() {
            return files;
        }

        @Override
        ConfigurableFileCollection getIfCreated() {
            return files;
        }

        @Override
        void replace(FileCollection files) {
            this.files.setFrom(files);
        }
    }
}
