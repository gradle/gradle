/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.shareddata;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@NonNullApi
class SharedDataStorage {
    private final Map<DataKey, Map<Path, Provider<?>>> dataByKeyAndProjectPath = new HashMap<>();

    @Nullable
    public <T> Provider<T> get(Path sourceProjectIdentityPath, Class<T> type, @Nullable String identifier) {
        @Nullable Map<Path, Provider<?>> dataWithKey = dataByKeyAndProjectPath.get(new DataKey(type, identifier));
        if (dataWithKey == null) {
            return null;
        }
        // Unchecked cast is OK since the calls to the code putting the items is type-checked with the same constraints
        return Cast.uncheckedCast(dataWithKey.get(sourceProjectIdentityPath));
    }

    public <T> void put(Project sourceProject, Class<T> type, @Nullable String identifier, Provider<T> dataProvider) {
        dataByKeyAndProjectPath.computeIfAbsent(new DataKey(type, identifier), key -> new HashMap<>()).compute(((ProjectInternal) sourceProject).getIdentityPath(), (key, oldValue) -> {
            if (oldValue != null) {
                throw new IllegalStateException("Shared data for type " + type + (identifier != null ? " (identifier '" + identifier + "')" : "") + " has already been registered in " + sourceProject);
            }
            return dataProvider;
        });
    }

    private static class DataKey {
        public final Class<?> type;

        @Nullable
        public final String identifier;

        private DataKey(Class<?> type, @Nullable String identifier) {
            this.type = type;
            this.identifier = identifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DataKey)) {
                return false;
            }
            DataKey dataKey = (DataKey) o;
            return Objects.equals(type, dataKey.type) && Objects.equals(identifier, dataKey.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, identifier);
        }
    }

}
