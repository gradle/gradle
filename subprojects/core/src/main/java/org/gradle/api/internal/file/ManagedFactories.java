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

package org.gradle.api.internal.file;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.DefaultFilePropertyFactory.FixedDirectory;
import org.gradle.internal.state.ManagedFactory;

import java.io.File;

public class ManagedFactories {

    public static class RegularFileManagedFactory implements ManagedFactory {
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            return type.cast(new DefaultFilePropertyFactory.FixedFile((File) state));
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(RegularFile.class);
        }
    }

    public static class RegularFilePropertyManagedFactory implements ManagedFactory {
        private final FileResolver fileResolver;

        public RegularFilePropertyManagedFactory(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            return type.cast(new DefaultFilePropertyFactory.DefaultRegularFileVar(fileResolver).value((RegularFile) state));
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(RegularFileProperty.class);
        }
    }

    public static class DirectoryManagedFactory implements ManagedFactory {
        private final FileResolver fileResolver;

        public DirectoryManagedFactory(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            return type.cast(new FixedDirectory((File) state, fileResolver));
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(Directory.class);
        }
    }

    public static class DirectoryPropertyManagedFactory implements ManagedFactory {
        private final FileResolver fileResolver;

        public DirectoryPropertyManagedFactory(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!canCreate(type)) {
                return null;
            }
            return type.cast(new DefaultFilePropertyFactory.DefaultDirectoryVar(fileResolver).value((Directory) state));
        }

        @Override
        public boolean canCreate(Class<?> type) {
            return type.isAssignableFrom(DirectoryProperty.class);
        }
    }
}
