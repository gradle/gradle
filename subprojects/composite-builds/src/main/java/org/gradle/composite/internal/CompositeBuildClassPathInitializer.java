/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.initialization.BuildIdentity;
import org.gradle.internal.service.ServiceRegistry;

import java.util.Set;

public class CompositeBuildClassPathInitializer implements ScriptClassPathInitializer {
    private final IncludedBuildTaskGraph includedBuildTaskGraph;
    private final ServiceRegistry serviceRegistry;
    private final IncludedBuildRegistry includedBuildRegistry;
    private BuildIdentifier currentBuild;

    public CompositeBuildClassPathInitializer(IncludedBuildRegistry includedBuildRegistry, IncludedBuildTaskGraph includedBuildTaskGraph, ServiceRegistry serviceRegistry) {
        this.includedBuildRegistry = includedBuildRegistry;
        this.includedBuildTaskGraph = includedBuildTaskGraph;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public void execute(Configuration classpath) {
        if (!includedBuildRegistry.hasIncludedBuilds()) {
            return;
        }

        ArtifactCollection artifacts = classpath.getIncoming().getArtifacts();
        for (ResolvedArtifactResult artifactResult : artifacts.getArtifacts()) {
            ComponentArtifactIdentifier componentArtifactIdentifier = artifactResult.getId();
            build(getCurrentBuild(), componentArtifactIdentifier);
        }
    }

    private BuildIdentifier getBuildIdentifier(CompositeProjectComponentArtifactMetadata artifact) {
        return artifact.getComponentId().getBuild();
    }

    private BuildIdentifier getCurrentBuild() {
        if (currentBuild == null) {
            currentBuild = serviceRegistry.get(BuildIdentity.class).getCurrentBuild();
        }
        return currentBuild;
    }

    public void build(BuildIdentifier requestingBuild, ComponentArtifactIdentifier artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            BuildIdentifier targetBuild = getBuildIdentifier(compositeBuildArtifact);
            Set<String> tasks = compositeBuildArtifact.getTasks();
            for (String taskName : tasks) {
                includedBuildTaskGraph.addTask(requestingBuild, targetBuild, taskName);
            }
            for (String taskName : tasks) {
                includedBuildTaskGraph.awaitCompletion(targetBuild, taskName);
            }
        }
    }
}
