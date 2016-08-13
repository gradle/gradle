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

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.invocation.BuildController;

public class CompositeSubstitutionsActionRunner {
    private final CompositeBuildContext context;

    public CompositeSubstitutionsActionRunner(CompositeBuildContext context) {
        this.context = context;
    }

    public void run(BuildController buildController) {
        GradleInternal gradle = buildController.configure();
        ProjectInternal rootProject = gradle.getRootProject();

        String participantName = rootProject.getName();
        for (Project project : rootProject.getAllprojects()) {
            registerProject(participantName, (ProjectInternal) project);
        }
    }

    private void registerProject(String buildName, ProjectInternal project) {
        LocalComponentRegistry localComponentRegistry = project.getServices().get(LocalComponentRegistry.class);
        ProjectComponentIdentifier originalIdentifier = DefaultProjectComponentIdentifier.newId(project.getPath());
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(originalIdentifier);
        ProjectComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(createExternalProjectPath(buildName, project.getPath()));
        context.registerSubstitution(originalComponent.getId(), componentIdentifier);
    }

    private String createExternalProjectPath(String buildName, String projectPath) {
        return buildName + ":" + projectPath;
    }
}
