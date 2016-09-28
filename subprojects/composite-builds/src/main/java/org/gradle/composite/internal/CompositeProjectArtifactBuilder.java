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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactBuilder;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetadata;

import java.util.Collection;

class CompositeProjectArtifactBuilder implements ProjectArtifactBuilder {
    private final Multimap<ProjectComponentIdentifier, String> tasksForBuild = LinkedHashMultimap.create();
    private final IncludedBuildExecuter includedBuildExecuter;

    CompositeProjectArtifactBuilder(IncludedBuildExecuter includedBuildExecuter) {
        this.includedBuildExecuter = includedBuildExecuter;
    }

    @Override
    public void willBuild(ComponentArtifactMetadata artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            ProjectComponentIdentifier buildId = getBuildIdentifier(compositeBuildArtifact);
            addTasksForBuild(buildId, compositeBuildArtifact);
        }
    }

    @Override
    public void build(ComponentArtifactMetadata artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            CompositeProjectComponentArtifactMetadata compositeBuildArtifact = (CompositeProjectComponentArtifactMetadata) artifact;
            ProjectComponentIdentifier buildId = getBuildIdentifier(compositeBuildArtifact);
            Collection<String> tasksToExecute = addTasksForBuild(buildId, compositeBuildArtifact);
            includedBuildExecuter.execute(buildId, tasksToExecute);
        }
    }

    private synchronized Collection<String> addTasksForBuild(ProjectComponentIdentifier buildId, CompositeProjectComponentArtifactMetadata compositeBuildArtifact) {
        tasksForBuild.putAll(buildId, compositeBuildArtifact.getTasks());
        return tasksForBuild.get(buildId);
    }

    private ProjectComponentIdentifier getBuildIdentifier(CompositeProjectComponentArtifactMetadata artifact) {
        return DefaultProjectComponentIdentifier.rootId(artifact.getComponentId());
    }

}
