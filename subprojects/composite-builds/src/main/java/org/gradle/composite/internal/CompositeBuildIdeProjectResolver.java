/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactBuilder;
import org.gradle.initialization.BuildIdentity;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;

/**
 * Resolves and builds IDE metadata artifacts from other projects within the same build.
 *
 * When building IDE metadata artifacts for an included build, the regular (jar) artifacts for that build are not
 * created. To ensure this, a separate {@link IncludedBuildArtifactBuilder} instance is used for building.
 * (The session-scoped {@link IncludedBuildArtifactBuilder} instance is primed to create all included build artifacts in a single execution.)
 */
public class CompositeBuildIdeProjectResolver {
    private final LocalComponentRegistry registry;
    private final ProjectArtifactBuilder artifactBuilder;

    public static CompositeBuildIdeProjectResolver from(ServiceRegistry services) {
        return new CompositeBuildIdeProjectResolver(services.get(LocalComponentRegistry.class), services.get(IncludedBuildExecuter.class), services.get(BuildIdentity.class));
    }

    public CompositeBuildIdeProjectResolver(LocalComponentRegistry registry, IncludedBuildExecuter executer, BuildIdentity buildIdentity) {
        this.registry = registry;
        // Can't use the session-scope `IncludedBuildArtifactBuilder`, because we don't want to be execute jar tasks (which are pre-registered)
        artifactBuilder = new CompositeProjectArtifactBuilder(new IncludedBuildArtifactBuilder(executer), buildIdentity);
    }

    /**
     * Finds an IDE metadata artifact with the specified type. Does not execute tasks to build the artifact.
     *
     * IDE metadata artifacts are registered by IDE plugins via {@link org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider#registerAdditionalArtifact(org.gradle.api.artifacts.component.ProjectComponentIdentifier, org.gradle.internal.component.local.model.LocalComponentArtifactMetadata)}
     */
    public LocalComponentArtifactMetadata findArtifact(ProjectComponentIdentifier project, String type) {
        for (LocalComponentArtifactMetadata artifactMetaData : registry.getAdditionalArtifacts(project)) {
            if (artifactMetaData.getName().getType().equals(type)) {
                return artifactMetaData;
            }
        }
        return null;
    }

    /**
     * Finds an IDE metadata artifact with the specified type, and executes tasks to build the artifact file.
     */
    public File buildArtifactFile(ProjectComponentIdentifier project, String type) {
        LocalComponentArtifactMetadata artifactMetaData = findArtifact(project, type);
        if (artifactMetaData == null) {
            return null;
        }
        artifactBuilder.build(artifactMetaData);
        return artifactMetaData.getFile();
    }
}
