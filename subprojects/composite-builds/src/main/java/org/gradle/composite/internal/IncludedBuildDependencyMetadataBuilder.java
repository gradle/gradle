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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import java.io.File;

public class IncludedBuildDependencyMetadataBuilder {
    public LocalComponentMetadata build(CompositeBuildParticipantBuildState build, ProjectComponentIdentifier projectIdentifier) {
        GradleInternal gradle = build.getMutableModel();
        LocalComponentRegistry localComponentRegistry = gradle.getServices().get(LocalComponentRegistry.class);
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(projectIdentifier);

        ProjectComponentIdentifier foreignIdentifier = build.idToReferenceProjectFromAnotherBuild(projectIdentifier);
        return createCompositeCopy(foreignIdentifier, originalComponent);
    }

    private LocalComponentMetadata createCompositeCopy(final ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetadata originalComponentMetadata) {
        return originalComponentMetadata.copy(componentIdentifier, originalArtifact -> {
            // Currently need to resolve the file, so that the artifact can be used in both a script classpath and the main build. Instead, this should be resolved as required
            File file = originalArtifact.getFile();
            return new CompositeProjectComponentArtifactMetadata(componentIdentifier, originalArtifact, file);
        });
    }
}
