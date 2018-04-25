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

import com.google.common.collect.Sets;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;

import java.io.File;
import java.util.Set;

public class IncludedBuildDependencyMetadataBuilder {
    public LocalComponentMetadata build(IncludedBuildState build, ProjectComponentIdentifier projectIdentifier) {
        GradleInternal gradle = build.getConfiguredBuild();
        LocalComponentRegistry localComponentRegistry = gradle.getServices().get(LocalComponentRegistry.class);
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(projectIdentifier);

        ProjectComponentIdentifier foreignIdentifier = build.idToReferenceProjectFromAnotherBuild(projectIdentifier.getProjectPath());
        return createCompositeCopy(foreignIdentifier, originalComponent);
    }

    private LocalComponentMetadata createCompositeCopy(final ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetadata originalComponentMetadata) {
        return originalComponentMetadata.copy(componentIdentifier, new Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata>() {
            @Override
            public LocalComponentArtifactMetadata transform(LocalComponentArtifactMetadata originalArtifact) {
                File artifactFile = originalArtifact.getFile();
                Set<String> targetTasks = getArtifactTasks(originalArtifact);
                return new CompositeProjectComponentArtifactMetadata(componentIdentifier, originalArtifact.getName(), artifactFile, targetTasks);
            }
        }, new Transformer<LocalOriginDependencyMetadata, LocalOriginDependencyMetadata>() {
            @Override
            public LocalOriginDependencyMetadata transform(LocalOriginDependencyMetadata originalDependency) {
                return originalDependency;
            }
        });
    }

    private Set<String> getArtifactTasks(ComponentArtifactMetadata artifactMetaData) {
        Set<String> taskPaths = Sets.newLinkedHashSet();
        Set<? extends Task> tasks = artifactMetaData.getBuildDependencies().getDependencies(null);
        for (Task task : tasks) {
            taskPaths.add(task.getPath());
        }
        return taskPaths;
    }
}
