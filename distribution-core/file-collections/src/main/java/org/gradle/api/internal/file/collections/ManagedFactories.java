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

import com.google.common.base.Objects;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;

public class ManagedFactories {
    public static class ConfigurableFileCollectionManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = ConfigurableFileCollection.class;
        public static final int FACTORY_ID = Objects.hashCode(PUBLIC_TYPE.getName());

        private final FileCollectionFactory fileCollectionFactory;

        public ConfigurableFileCollectionManagedFactory(FileCollectionFactory fileCollectionFactory) {
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO - should retain display name
            return type.cast(fileCollectionFactory.configurableFiles().from(state));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }
}
