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

import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.internal.build.BuildState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompositeBuildClassPathInitializer implements ScriptClassPathInitializer {
    private final IncludedBuildTaskGraph includedBuildTaskGraph;
    private final BuildIdentifier currentBuild;

    public CompositeBuildClassPathInitializer(IncludedBuildTaskGraph includedBuildTaskGraph, BuildState currentBuild) {
        this.includedBuildTaskGraph = includedBuildTaskGraph;
        this.currentBuild = currentBuild.getBuildIdentifier();
    }

    @Override
    public void execute(Configuration classpath) {
        ArtifactCollection artifacts = classpath.getIncoming().getArtifacts();
        List<CompositeProjectComponentArtifactMetadata> localArtifacts = new ArrayList<>();
        for (ResolvedArtifactResult artifactResult : artifacts.getArtifacts()) {
            ComponentArtifactIdentifier artifactIdentifier = artifactResult.getId();
            if (artifactIdentifier instanceof CompositeProjectComponentArtifactMetadata) {
                localArtifacts.add((CompositeProjectComponentArtifactMetadata) artifactIdentifier);
            }
        }
        if (!localArtifacts.isEmpty()) {
            includedBuildTaskGraph.withNewTaskGraph(() -> {
                includedBuildTaskGraph.prepareTaskGraph(() -> {
                    for (CompositeProjectComponentArtifactMetadata artifact : localArtifacts) {
                        scheduleTasks(currentBuild, artifact);
                    }
                    includedBuildTaskGraph.populateTaskGraphs();
                });
                includedBuildTaskGraph.runScheduledTasks();
                return null;
            });
        }
    }

    public void scheduleTasks(BuildIdentifier requestingBuild, CompositeProjectComponentArtifactMetadata artifact) {
        BuildIdentifier targetBuild = artifact.getComponentId().getBuild();
        assert !requestingBuild.equals(targetBuild);
        Set<? extends Task> tasks = artifact.getBuildDependencies().getDependencies(null);
        for (Task task : tasks) {
            includedBuildTaskGraph.locateTask(targetBuild, (TaskInternal) task).queueForExecution();
        }
    }
}
