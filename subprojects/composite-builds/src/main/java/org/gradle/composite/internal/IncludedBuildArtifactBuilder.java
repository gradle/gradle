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

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.initialization.IncludedBuildTaskGraph;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.LocationAwareException;

class IncludedBuildArtifactBuilder {
    private final IncludedBuildTaskGraph includedBuildTaskGraph;

    IncludedBuildArtifactBuilder(IncludedBuildTaskGraph includedBuildTaskGraph) {
        this.includedBuildTaskGraph = includedBuildTaskGraph;
    }

    public void willBuild(BuildIdentifier requestingBuild, ComponentArtifactMetadata artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            BuildIdentifier buildId = getBuildIdentifier(compositeBuildArtifact);
            includedBuildTaskGraph.addTasks(requestingBuild, buildId, compositeBuildArtifact.getTasks());
        }
    }

    public void build(BuildIdentifier requestingBuild, ComponentArtifactMetadata artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            BuildIdentifier targetBuild = getBuildIdentifier(compositeBuildArtifact);
            includedBuildTaskGraph.addTasks(requestingBuild, targetBuild, compositeBuildArtifact.getTasks());
            execute(requestingBuild, targetBuild);
        }
    }

    private void execute(BuildIdentifier requestingBuild, BuildIdentifier targetBuild) {
        try {
            includedBuildTaskGraph.awaitCompletion(requestingBuild, targetBuild);
        } catch (ReportedException e) {
            throw contextualizeFailure(targetBuild, e);
        }
    }

    private BuildIdentifier getBuildIdentifier(CompositeProjectComponentArtifactMetadata artifact) {
        return artifact.getComponentId().getBuild();
    }

    private RuntimeException contextualizeFailure(BuildIdentifier buildId, ReportedException e) {
        if (e.getCause() instanceof LocationAwareException) {
            LocationAwareException lae = (LocationAwareException) e.getCause();
            IncludedBuildArtifactException wrappedCause = new IncludedBuildArtifactException("Failed to build artifacts for " + buildId, lae.getCause());
            LocationAwareException newLae = new LocationAwareException(wrappedCause, lae.getSourceDisplayName(), lae.getLineNumber());
            return new ReportedException(newLae);
        }
        return e;
    }

    @Contextual
    private static class IncludedBuildArtifactException extends GradleException {
        public IncludedBuildArtifactException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
