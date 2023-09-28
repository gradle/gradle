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

package org.gradle.api.shareddata;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.provider.Provider;
import org.gradle.util.Path;

import javax.annotation.Nullable;

@NonNullApi
public interface ProjectSharedDataRegistry {
    <T> void registerSharedDataProducer(Class<T> dataType, @Nullable String dataIdentifier, Provider<T> dataProvider);

    <T> Provider<? extends T> obtainSharedData(Class<T> dataType, @Nullable String dataIdentifier, SingleSourceIdentifier dataSourceIdentifier);

    <T> Provider<? extends T> obtainSharedData(Class<T> dataType, SingleSourceIdentifier dataSourceIdentifier);

    // TODO: these should conveniently wrap heterogeneous project identifiers, to be used as `sharedData.obtainSharedData(..., sharedData.fromProject(id))`
    SingleSourceIdentifier fromProject(Project project);
    SingleSourceIdentifier fromProject(ProjectComponentIdentifier project);

    interface SingleSourceIdentifier {
        Path getSourceProjectIdentitiyPath();
    }
}
