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
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.execution.MultipleBuildFailures;
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
        boolean found = false;
        for (ResolvedArtifactResult artifactResult : artifacts.getArtifacts()) {
            ComponentArtifactIdentifier componentArtifactIdentifier = artifactResult.getId();
            found |= build(currentBuild, componentArtifactIdentifier);
        }
        if (found) {
            List<Throwable> taskFailures = new ArrayList<Throwable>();
            includedBuildTaskGraph.awaitTaskCompletion(taskFailures);
            if (!taskFailures.isEmpty()) {
                throw new MultipleBuildFailures(taskFailures);
            }
        }
    }

    public boolean build(BuildIdentifier requestingBuild, ComponentArtifactIdentifier artifact) {
        boolean found = false;
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            BuildIdentifier targetBuild = compositeBuildArtifact.getComponentId().getBuild();
            assert !requestingBuild.equals(targetBuild);
            Set<? extends Task> tasks = compositeBuildArtifact.getBuildDependencies().getDependencies(null);
            for (Task task : tasks) {
                includedBuildTaskGraph.addTask(requestingBuild, targetBuild, task.getPath());
            }
            found = true;
        }
        return found;
    }
}
