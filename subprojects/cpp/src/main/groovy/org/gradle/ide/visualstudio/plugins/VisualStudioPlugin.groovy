/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio.plugins
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.ide.visualstudio.VisualStudioExtension
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioExtension
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.ide.visualstudio.internal.rules.CreateVisualStudioModel
import org.gradle.ide.visualstudio.internal.rules.CreateVisualStudioTasks
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.ModelRules
import org.gradle.nativebinaries.internal.resolve.ProjectLocator
import org.gradle.nativebinaries.plugins.NativeBinariesModelPlugin

import javax.inject.Inject

@Incubating
class VisualStudioPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator
    private final ModelRules modelRules
    private final ProjectLocator projectLocator
    private final FileResolver fileResolver

    @Inject
    VisualStudioPlugin(Instantiator instantiator, ModelRules modelRules, ProjectLocator projectLocator, FileResolver fileResolver) {
        this.instantiator = instantiator
        this.modelRules = modelRules
        this.projectLocator = projectLocator
        this.fileResolver = fileResolver
    }

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesModelPlugin)

        modelRules.register("visualStudio", instantiator.newInstance(DefaultVisualStudioExtension, instantiator, projectLocator, fileResolver))
        modelRules.config("visualStudio", new IncludeBuildFileInProject(project))
        modelRules.rule(new CreateVisualStudioModel())
        modelRules.rule(new CreateVisualStudioTasks())
    }

    private class IncludeBuildFileInProject implements Action<VisualStudioExtension> {
        private final ProjectInternal project;

        IncludeBuildFileInProject(ProjectInternal project) {
            this.project = project
        }

        void execute(VisualStudioExtension extension) {
            extension.projects.all {
                if (project.getBuildFile() != null) {
                    ((DefaultVisualStudioProject) it).addSourceFile(project.getBuildFile())
                }
            }
        }
    }
}
