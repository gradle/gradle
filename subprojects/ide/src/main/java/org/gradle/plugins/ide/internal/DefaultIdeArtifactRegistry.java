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

package org.gradle.plugins.ide.internal;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.initialization.BuildIdentity;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultIdeArtifactRegistry implements IdeArtifactRegistry {
    private final ProjectLocalComponentProvider projectComponentProvider;
    private final ProjectPathRegistry projectPathRegistry;
    private final LocalComponentRegistry localComponentRegistry;
    private final DomainObjectContext domainObjectContext;
    private final BuildIdentity buildIdentity;
    private final FileOperations fileOperations;

    public DefaultIdeArtifactRegistry(ProjectLocalComponentProvider projectComponentProvider, ProjectPathRegistry projectPathRegistry, LocalComponentRegistry localComponentRegistry, DomainObjectContext domainObjectContext, BuildIdentity buildIdentity, FileOperations fileOperations) {
        this.projectComponentProvider = projectComponentProvider;
        this.projectPathRegistry = projectPathRegistry;
        this.localComponentRegistry = localComponentRegistry;
        this.domainObjectContext = domainObjectContext;
        this.buildIdentity = buildIdentity;
        this.fileOperations = fileOperations;
    }

    @Override
    public void registerIdeArtifact(PublishArtifact ideArtifact) {
        ProjectComponentIdentifier projectId = newProjectId(buildIdentity.getCurrentBuild(), domainObjectContext.getProjectPath().getPath());
        projectComponentProvider.registerAdditionalArtifact(projectId, new PublishArtifactLocalArtifactMetadata(projectId, ideArtifact));
    }

    @Override
    public List<LocalComponentArtifactMetadata> getIdeArtifactMetadata(String type) {
        List<LocalComponentArtifactMetadata> result = Lists.newArrayList();

        for (Path projectPath : projectPathRegistry.getAllExplicitProjectPaths()) {
            ProjectComponentIdentifier projectId = projectPathRegistry.getProjectComponentIdentifier(projectPath);
            Iterable<LocalComponentArtifactMetadata> additionalArtifacts = localComponentRegistry.getAdditionalArtifacts(projectId);
            for (LocalComponentArtifactMetadata artifactMetadata : additionalArtifacts) {
                if (artifactMetadata.getName().getType().equals(type)) {
                    result.add(artifactMetadata);
                }
            }
        }

        return result;
    }

    @Override
    public FileCollection getIdeArtifacts(final String type) {
        return fileOperations.files(new Callable<List<FileCollection>>() {
            @Override
            public List<FileCollection> call() {
                return CollectionUtils.collect(
                    getIdeArtifactMetadata(type),
                    new Transformer<FileCollection, LocalComponentArtifactMetadata>() {
                        @Override
                        public FileCollection transform(LocalComponentArtifactMetadata metadata) {
                            ConfigurableFileCollection result = fileOperations.files(metadata.getFile());
                            result.builtBy(metadata.getBuildDependencies());
                            return result;
                        }
                    });
            }
        });
    }
}
