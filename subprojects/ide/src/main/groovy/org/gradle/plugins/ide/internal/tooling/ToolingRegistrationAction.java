/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.project.ProjectConfigureAction;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

public class ToolingRegistrationAction implements ProjectConfigureAction {
    public void execute(ProjectInternal project) {
        ToolingModelBuilderRegistry modelBuilderRegistry = project.getServices().get(ToolingModelBuilderRegistry.class);
        ProjectPublicationRegistry projectPublicationRegistry = project.getServices().get(ProjectPublicationRegistry.class);

        GradleProjectBuilder gradleProjectBuilder  = new GradleProjectBuilder();
        IdeaModelBuilder ideaModelBuilder = new IdeaModelBuilder(gradleProjectBuilder);
        modelBuilderRegistry.register(new EclipseModelBuilder(gradleProjectBuilder));
        modelBuilderRegistry.register(ideaModelBuilder);
        modelBuilderRegistry.register(gradleProjectBuilder);
        modelBuilderRegistry.register(new GradleBuildBuilder());
        modelBuilderRegistry.register(new BasicIdeaModelBuilder(ideaModelBuilder));
        modelBuilderRegistry.register(new BuildInvocationsBuilder(gradleProjectBuilder));
        modelBuilderRegistry.register(new PublicationsBuilder(projectPublicationRegistry));
    }
}
