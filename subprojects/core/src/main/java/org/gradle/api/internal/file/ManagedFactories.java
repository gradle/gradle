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

    public static class RegularFileManagedFactory extends ManagedFactory.TypedManagedFactory {
        public RegularFileManagedFactory() {
            super(RegularFile.class);
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            return type.cast(new DefaultFilePropertyFactory.FixedFile((File) state));
        }
    }

    public static class RegularFilePropertyManagedFactory extends ManagedFactory.TypedManagedFactory {
        private final FileResolver fileResolver;

        public RegularFilePropertyManagedFactory(FileResolver fileResolver) {
            super(RegularFileProperty.class);
            this.fileResolver = fileResolver;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            return type.cast(new DefaultFilePropertyFactory.DefaultRegularFileVar(fileResolver).value((RegularFile) state));
        }
    }

    public static class DirectoryManagedFactory extends ManagedFactory.TypedManagedFactory {
        private final FileResolver fileResolver;

        public DirectoryManagedFactory(FileResolver fileResolver) {
            super(Directory.class);
            this.fileResolver = fileResolver;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            return type.cast(new FixedDirectory((File) state, fileResolver));
        }
    }

    public static class DirectoryPropertyManagedFactory extends ManagedFactory.TypedManagedFactory {
        private final FileResolver fileResolver;

        public DirectoryPropertyManagedFactory(FileResolver fileResolver) {
            super(DirectoryProperty.class);
            this.fileResolver = fileResolver;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(publicType)) {
                return null;
            }
            return type.cast(new DefaultFilePropertyFactory.DefaultDirectoryVar(fileResolver).value((Directory) state));
        }
    }
}
