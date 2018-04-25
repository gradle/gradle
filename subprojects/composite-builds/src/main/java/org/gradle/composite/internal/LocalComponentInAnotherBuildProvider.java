/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import java.util.concurrent.ExecutionException;

public class LocalComponentInAnotherBuildProvider implements LocalComponentProvider {
    private final IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder;
    private final BuildStateRegistry buildRegistry;
    private final LoadingCache<ProjectComponentIdentifier, LocalComponentMetadata> projectMetadata = CacheBuilder.newBuilder().build(new CacheLoader<ProjectComponentIdentifier, LocalComponentMetadata>() {
        @Override
        public LocalComponentMetadata load(ProjectComponentIdentifier projectIdentifier) {
            return getRegisteredProject(projectIdentifier);
        }
    });

    public LocalComponentInAnotherBuildProvider(BuildStateRegistry buildRegistry, IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder) {
        this.dependencyMetadataBuilder = dependencyMetadataBuilder;
        this.buildRegistry = buildRegistry;
    }

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier project) {
        try {
            return projectMetadata.get(project);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private LocalComponentMetadata getRegisteredProject(ProjectComponentIdentifier project) {
        IncludedBuildState includedBuild = buildRegistry.getIncludedBuild(project.getBuild());
        return dependencyMetadataBuilder.build(includedBuild, project);
    }
}
