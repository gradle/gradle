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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

public class ManagedFactories {
    public static class ConfigurableFileCollectionManagedFactory implements ManagedFactory {
        private static final Class<?> PUBLIC_TYPE = ConfigurableFileCollection.class;
        private static final Class<?> IMPL_TYPE = DefaultConfigurableFileCollection.class;
        public static final int FACTORY_ID = Objects.hashCode(IMPL_TYPE.getName());

        private final PathToFileResolver resolver;
        private final TaskDependencyFactory taskDependencyFactory;
        private final Factory<PatternSet> patternSetFactory;

        public ConfigurableFileCollectionManagedFactory(FileResolver resolver, TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory) {
            this.resolver = resolver;
            this.taskDependencyFactory = taskDependencyFactory;
            this.patternSetFactory = patternSetFactory;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            if (!type.isAssignableFrom(PUBLIC_TYPE)) {
                return null;
            }
            // TODO - should retain display name
            return type.cast(new DefaultConfigurableFileCollection(null, resolver, taskDependencyFactory, patternSetFactory, (Set<File>) state));
        }

        @Override
        public int getId() {
            return FACTORY_ID;
        }
    }
}
