/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.plugins;

import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.internal.NativeSpecVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.platform.base.BinaryContainer;

class VisualStudioPluginRules extends RuleSource {
    @Model
    public static VisualStudioExtensionInternal visualStudio(ExtensionContainer extensionContainer) {
        return (VisualStudioExtensionInternal) extensionContainer.getByType(VisualStudioExtension.class);
    }

    @Mutate
    public static void createVisualStudioModelForBinaries(VisualStudioExtensionInternal visualStudioExtension, BinaryContainer binaries, ProjectIdentifier projectIdentifier, ServiceRegistry serviceRegistry) {
        for (NativeBinarySpec binary : binaries.withType(NativeBinarySpec.class)) {
            visualStudioExtension.getProjectRegistry().addProjectConfiguration(new NativeSpecVisualStudioTargetBinary(binary));
        }

        if (isRoot(projectIdentifier)) {
            ensureSubprojectsAreRealized(projectIdentifier, serviceRegistry);
        }
    }

    @Mutate
    public static void realizeExtension(TaskContainer tasks, VisualStudioExtensionInternal visualStudioExtensionInternal) {
        // Dummy rule to cause the extension to be realized
    }

    // This ensures that subprojects are realized and register their project and project configuration IDE artifacts
    private static void ensureSubprojectsAreRealized(ProjectIdentifier projectIdentifier, ServiceRegistry serviceRegistry) {
        ProjectModelResolver projectModelResolver = serviceRegistry.get(ProjectModelResolver.class);
        ProjectRegistry<ProjectInternal> projectRegistry = Cast.uncheckedCast(serviceRegistry.get(ProjectRegistry.class));

        for (ProjectInternal subproject : projectRegistry.getSubProjects(projectIdentifier.getPath())) {
            projectModelResolver.resolveProjectModel(subproject.getPath()).find("visualStudio", VisualStudioExtension.class);
        }
    }

    private static boolean isRoot(ProjectIdentifier projectIdentifier) {
        return projectIdentifier.getParentIdentifier() == null;
    }




}
