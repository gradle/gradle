/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Action;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;
import org.gradle.tooling.internal.consumer.converters.*;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier;

public class HasCompatibilityMapping {

    private final Action<SourceObjectMapping> taskPropertyHandlerMapper;
    private final Action<SourceObjectMapping> ideaProjectCompatibilityMapper;
    private final Action<SourceObjectMapping> eclipseProjectDependencyCompatibilityMapper;
    private final Action<SourceObjectMapping> gradleProjectIdentifierMapper;

    public HasCompatibilityMapping(VersionDetails versionDetails) {
        taskPropertyHandlerMapper = new TaskDisplayNameCompatibilityMapping(versionDetails);
        ideaProjectCompatibilityMapper = new IdeaModelCompatibilityMapping(versionDetails);
        eclipseProjectDependencyCompatibilityMapper = new EclipseModelCompatibilityMapping(versionDetails);
        gradleProjectIdentifierMapper = new GradleProjectIdentifierCompatibilityMapping();
    }

    public Action<SourceObjectMapping> getCompatibilityMapping(ConsumerOperationParameters parameters) {
        ProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(parameters.getBuildIdentifier(), ":");
        return getCompatibilityMapping(projectIdentifier);
    }

    public Action<SourceObjectMapping> getCompatibilityMapping(ProjectIdentifier projectIdentifier) {
        FixedBuildIdentifierProvider identifierProvider = new FixedBuildIdentifierProvider(projectIdentifier);
        return getCompatibilityMapping(identifierProvider);
    }

    private Action<SourceObjectMapping> getCompatibilityMapping(Action<SourceObjectMapping> requestScopedMapping) {
        return CompositeCompatibilityMapping.builder()
            .add(taskPropertyHandlerMapper)
            .add(ideaProjectCompatibilityMapper)
            .add(eclipseProjectDependencyCompatibilityMapper)
            .add(gradleProjectIdentifierMapper)
            .add(requestScopedMapping)
            .build();
    }
}
