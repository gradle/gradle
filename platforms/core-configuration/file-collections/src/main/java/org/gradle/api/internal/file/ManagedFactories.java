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

import com.google.common.base.Objects;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCollectionProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.collections.DefaultFileCollectionProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;
import java.io.File;

public class ManagedFactories {

    public static class FileCollectionPropertyFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = FileCollectionProperty.class;
        private static final Class<?> IMPL_TYPE = DefaultFileCollectionProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());
        private final FilePropertyFactory filePropertyFactory;

        public FileCollectionPropertyFactory(FilePropertyFactory factory) {
            this.filePropertyFactory = factory;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO-RC - should retain display name
            Provider<? extends FileCollection> asProvider = Cast.uncheckedNonnullCast(state);
            return type.cast(filePropertyFactory.newFileCollectionProperty(FileCollection.class).value(asProvider));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class RegularFileManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = RegularFile.class;
        public static final int FACTORY_ID = Objects.hashCode(PUBLIC_TYPE.getName());
        private final FileFactory fileFactory;

        public RegularFileManagedFactory(FileFactory fileFactory) {
            this.fileFactory = fileFactory;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            return type.cast(fileFactory.file((File) state));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class RegularFilePropertyManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = RegularFileProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(PUBLIC_TYPE.getName());

        private final FilePropertyFactory filePropertyFactory;

        public RegularFilePropertyManagedFactory(FilePropertyFactory filePropertyFactory) {
            this.filePropertyFactory = filePropertyFactory;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            return type.cast(filePropertyFactory.newFileProperty().value(Cast.<Provider<RegularFile>>uncheckedNonnullCast(state)));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class DirectoryManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = Directory.class;
        public static final int FACTORY_ID = Objects.hashCode(PUBLIC_TYPE.getName());

        private final FileFactory fileFactory;

        public DirectoryManagedFactory(FileFactory fileFactory) {
            this.fileFactory = fileFactory;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            return type.cast(fileFactory.dir((File) state));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }

    public static class DirectoryPropertyManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = DirectoryProperty.class;
        public static final int FACTORY_ID = Objects.hashCode(PUBLIC_TYPE.getName());

        private final FilePropertyFactory filePropertyFactory;

        public DirectoryPropertyManagedFactory(FilePropertyFactory filePropertyFactory) {
            this.filePropertyFactory = filePropertyFactory;
        }

        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            return type.cast(filePropertyFactory.newDirectoryProperty().value(Cast.<Provider<Directory>>uncheckedNonnullCast(state)));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }
}
