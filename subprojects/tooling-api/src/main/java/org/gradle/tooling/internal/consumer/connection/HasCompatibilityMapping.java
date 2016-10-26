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

import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.consumer.converters.BasicGradleProjectIdentifierMixin;
import org.gradle.tooling.internal.consumer.converters.FixedBuildIdentifierProvider;
import org.gradle.tooling.internal.consumer.converters.GradleProjectIdentifierMixin;
import org.gradle.tooling.internal.consumer.converters.IdeaModuleDependencyTargetNameMixin;
import org.gradle.tooling.internal.consumer.converters.IdeaProjectJavaLanguageSettingsMixin;
import org.gradle.tooling.internal.consumer.converters.IncludedBuildsMixin;
import org.gradle.tooling.internal.consumer.converters.TaskDisplayNameMixin;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaProject;

public class HasCompatibilityMapping {

    public <T> ViewBuilder<T> applyCompatibilityMapping(ViewBuilder<T> viewBuilder, ConsumerOperationParameters parameters) {
        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(parameters.getProjectDir(), ":");
        return applyCompatibilityMapping(viewBuilder, projectIdentifier);
    }

    public <T> ViewBuilder<T> applyCompatibilityMapping(ViewBuilder<T> viewBuilder, DefaultProjectIdentifier projectIdentifier) {
        viewBuilder.mixInTo(GradleProject.class, new GradleProjectIdentifierMixin(projectIdentifier.getBuildIdentifier()));
        viewBuilder.mixInTo(BasicGradleProject.class, new BasicGradleProjectIdentifierMixin(projectIdentifier.getBuildIdentifier()));
        FixedBuildIdentifierProvider identifierProvider = new FixedBuildIdentifierProvider(projectIdentifier);
        identifierProvider.applyTo(viewBuilder);
        viewBuilder.mixInTo(GradleTask.class, TaskDisplayNameMixin.class);
        viewBuilder.mixInTo(IdeaProject.class, IdeaProjectJavaLanguageSettingsMixin.class);
        viewBuilder.mixInTo(IdeaDependency.class, IdeaModuleDependencyTargetNameMixin.class);
        viewBuilder.mixInTo(GradleBuild.class, new IncludedBuildsMixin());
        return viewBuilder;
    }
}
