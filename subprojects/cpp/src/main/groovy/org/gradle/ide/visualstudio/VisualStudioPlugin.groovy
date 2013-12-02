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
package org.gradle.ide.visualstudio
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer
import org.gradle.ide.visualstudio.internal.DefaultProjectFinder
import org.gradle.ide.visualstudio.internal.VisualStudioExtension
import org.gradle.ide.visualstudio.internal.rules.CreateVisualStudioModel
import org.gradle.ide.visualstudio.internal.rules.CreateVisualStudioTasks
import org.gradle.internal.reflect.Instantiator
import org.gradle.model.ModelRule
import org.gradle.model.ModelRules
import org.gradle.model.internal.Inputs
import org.gradle.model.internal.ModelCreator
import org.gradle.nativebinaries.FlavorContainer
import org.gradle.nativebinaries.plugins.NativeBinariesModelPlugin

import javax.inject.Inject

@Incubating
class VisualStudioPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator
    private final ModelRules modelRules

    @Inject
    VisualStudioPlugin(Instantiator instantiator, ModelRules modelRules) {
        this.instantiator = instantiator
        this.modelRules = modelRules
    }

    void apply(ProjectInternal project) {
        project.plugins.apply(NativeBinariesModelPlugin)

        project.modelRegistry.create("visualStudio", ["flavors"], new VisualStudioExtensionFactory(instantiator, new DefaultProjectFinder(project), project.getFileResolver()))
        modelRules.rule(new CreateVisualStudioModel())
        modelRules.rule(new CreateVisualStudioTasks())
        modelRules.rule(new CloseVisualStudioForTasks());

        project.task("cleanVisualStudio", type: Delete) {
            delete "visualStudio"
        }
    }

    private static class VisualStudioExtensionFactory implements ModelCreator<VisualStudioExtension> {
        private final Instantiator instantiator;
        private final ProjectFinder projectFinder;
        private final FileResolver fileResolver;

        public VisualStudioExtensionFactory(Instantiator instantiator, ProjectFinder projectFinder, FileResolver fileResolver) {
            this.instantiator = instantiator;
            this.projectFinder = projectFinder;
            this.fileResolver = fileResolver;
        }

        VisualStudioExtension create(Inputs inputs) {
            FlavorContainer flavors = inputs.get(0, FlavorContainer)
            return instantiator.newInstance(VisualStudioExtension.class, instantiator, projectFinder, fileResolver, flavors);
        }

        Class<VisualStudioExtension> getType() {
            return VisualStudioExtension
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    private static class CloseVisualStudioForTasks extends ModelRule {
        void closeForTasks(TaskContainer tasks, VisualStudioExtension extension) {
            // nothing needed here
        }
    }
}

