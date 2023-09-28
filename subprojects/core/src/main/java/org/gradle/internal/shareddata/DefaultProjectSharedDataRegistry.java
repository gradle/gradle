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
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.provider.Provider;
import org.gradle.api.shareddata.ProjectSharedDataRegistry;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

@NonNullApi
public class DefaultProjectSharedDataRegistry implements ProjectSharedDataRegistry {
    private final ProjectInternal usedInProject;
    private final SharedDataRegistry globalSharedData;
    private final ProjectStateRegistry projectStateRegistry;

    @Inject
    public DefaultProjectSharedDataRegistry(ProjectInternal usedInProject, SharedDataRegistry globalSharedData, ProjectStateRegistry projectStateRegistry) {
        this.usedInProject = usedInProject;
        this.globalSharedData = globalSharedData;
        this.projectStateRegistry = projectStateRegistry;
    }

    @Override
    public <T> void registerSharedDataProducer(Class<T> dataType, @Nullable String dataIdentifier, Provider<T> dataProvider) {
        globalSharedData.registerSharedDataProducer(usedInProject, dataType, dataIdentifier, dataProvider);
    }

    @Override
    public <T> Provider<? extends T> obtainSharedData(Class<T> dataType, @Nullable String dataIdentifier, SingleSourceIdentifier dataSourceIdentifier) {
        // TODO: check composite builds
        projectStateRegistry.stateFor(dataSourceIdentifier.getSourceProjectIdentitiyPath()).ensureConfigured();

        return globalSharedData.obtainData(usedInProject, dataType, dataIdentifier, dataSourceIdentifier);
    }

    @Override
    public <T> Provider<? extends T> obtainSharedData(Class<T> dataType, SingleSourceIdentifier dataSourceIdentifier) {
        return obtainSharedData(dataType, null, dataSourceIdentifier);
    }

    @Override
    public SingleSourceIdentifier fromProject(Project project) {
        return new DefaultSharedDataSingleSourceIdentifier(((ProjectInternal) project).getIdentityPath().getPath());
    }

    @Override
    public SingleSourceIdentifier fromProject(ProjectComponentIdentifier project) {
        return new DefaultSharedDataSingleSourceIdentifier(project.getBuildTreePath());
    }
}
