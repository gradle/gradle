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
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public interface SharedDataStorage extends HoldsProjectState {
    @Nullable
    <T> Provider<T> get(Path sourceProjectIdentityPath, Class<T> type, @Nullable String identifier);

    <T> void put(Project sourceProject, Class<T> type, @Nullable String identifier, Provider<T> dataProvider);

    Map<DataKey, Provider<?>> getProjectData(Path sourceProjectIdentitityPath);

    class DataKey {
        public final Class<?> type;

        @Nullable
        public final String identifier;

        DataKey(Class<?> type, @Nullable String identifier) {
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
