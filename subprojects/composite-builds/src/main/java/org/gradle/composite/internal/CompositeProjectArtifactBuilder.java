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

class CompositeProjectArtifactBuilder implements ProjectArtifactBuilder {
    private final Multimap<ProjectComponentIdentifier, String> tasksForBuild = LinkedHashMultimap.create();
    private final IncludedBuildExecuter includedBuildExecuter;

    CompositeProjectArtifactBuilder(IncludedBuildExecuter includedBuildExecuter) {
        this.includedBuildExecuter = includedBuildExecuter;
    }

    @Override
    public void willBuild(ComponentArtifactMetadata artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            findBuildAndRegisterTasks((CompositeProjectComponentArtifactMetadata) artifact);
        }
    }

    @Override
    public void build(ComponentArtifactMetadata artifact) {
        if (artifact instanceof CompositeProjectComponentArtifactMetadata) {
            ProjectComponentIdentifier buildId = findBuildAndRegisterTasks((CompositeProjectComponentArtifactMetadata) artifact);
            includedBuildExecuter.execute(buildId, tasksForBuild.get(buildId));
        }
    }

    private ProjectComponentIdentifier findBuildAndRegisterTasks(CompositeProjectComponentArtifactMetadata artifact) {
        ProjectComponentIdentifier buildId = getBuildIdentifier(artifact.getComponentId());
        tasksForBuild.putAll(buildId, artifact.getTasks());
        return buildId;
    }

    private ProjectComponentIdentifier getBuildIdentifier(ProjectComponentIdentifier project) {
        // TODO:DAZ Introduce a properly typed ComponentIdentifier for project components in a composite
        String buildName = project.getProjectPath().split("::", 2)[0];
        return DefaultProjectComponentIdentifier.newId(buildName + "::");
    }

}
