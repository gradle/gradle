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
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;

public class ManagedFactories {
    public static class ConfigurableFileCollectionManagedFactory implements ManagedFactory {
        public static final int FACTORY_ID = Objects.hashCode(ConfigurableFileCollectionManagedFactory.class.getName());

        private final FileCollectionFactory fileCollectionFactory;

        public ConfigurableFileCollectionManagedFactory(FileCollectionFactory fileCollectionFactory) {
            this.fileCollectionFactory = fileCollectionFactory;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (type.isAssignableFrom(ConfigurableFileCollection.class)) {
                // TODO - should retain display name
                return type.cast(fileCollectionFactory.configurableFiles().from(state));
            }
            if (type.isAssignableFrom(ConfigurableFileTree.class)) {
                // TODO - should retain display name
                DefaultConfigurableFileTree.State fileTreeState = (DefaultConfigurableFileTree.State) state;
                ConfigurableFileTreeInternal fileTree = (ConfigurableFileTreeInternal) fileCollectionFactory.fileTree();
                fileTree.from(fileTreeState.roots);
                fileTree.getPatternSet().copyFrom(fileTreeState.patternSet);
                return type.cast(fileTree);
            }
            return null;
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }
}
