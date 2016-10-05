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
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.model.internal.DefaultProjectIdentifier;
import org.gradle.tooling.internal.consumer.converters.BasicGradleProjectIdentifierMixin;
import org.gradle.tooling.internal.consumer.converters.FixedProjectIdentifierProvider;
import org.gradle.tooling.internal.consumer.converters.GradleProjectIdentifierMixin;
import org.gradle.tooling.internal.consumer.converters.IdeaModelCompatibilityMapping;
import org.gradle.tooling.internal.consumer.converters.TaskDisplayNameCompatibilityMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;

public class HasCompatibilityMapping {

    private final Action<ViewBuilder<?>> taskPropertyHandlerMapper;
    private final Action<ViewBuilder<?>> ideaProjectCompatibilityMapper;

    public HasCompatibilityMapping(VersionDetails versionDetails) {
        taskPropertyHandlerMapper = new TaskDisplayNameCompatibilityMapping(versionDetails);
        ideaProjectCompatibilityMapper = new IdeaModelCompatibilityMapping(versionDetails);
    }

    public <T> ViewBuilder<T> applyCompatibilityMapping(ViewBuilder<T> viewBuilder, ProjectIdentifier projectIdentifier) {
        BuildIdentifier buildIdentifier = projectIdentifier.getBuildIdentifier();
        viewBuilder.mixInTo(GradleProject.class, new GradleProjectIdentifierMixin(buildIdentifier));
        viewBuilder.mixInTo(BasicGradleProject.class, new BasicGradleProjectIdentifierMixin(buildIdentifier));
        new FixedProjectIdentifierProvider(projectIdentifier).applyTo(viewBuilder);
        taskPropertyHandlerMapper.execute(viewBuilder);
        ideaProjectCompatibilityMapper.execute(viewBuilder);
        return viewBuilder;
    }

    public <T> ViewBuilder<T> applyCompatibilityMapping(ViewBuilder<T> viewBuilder, BuildIdentifier buildIdentifier) {
        return applyCompatibilityMapping(viewBuilder, new DefaultProjectIdentifier(buildIdentifier, ":"));
    }
}
