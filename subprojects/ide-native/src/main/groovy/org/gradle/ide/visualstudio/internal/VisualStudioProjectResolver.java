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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelType;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.resolve.ProjectLocator;

public class VisualStudioProjectResolver {
    private final ProjectLocator projectLocator;

    public VisualStudioProjectResolver(ProjectLocator projectLocator) {
        this.projectLocator = projectLocator;
    }

    public VisualStudioProjectConfiguration lookupProjectConfiguration(NativeBinarySpec nativeBinary) {
        // Looks in the correct project registry for this binary
        ProjectInternal componentProject = getComponentProject(nativeBinary);
        VisualStudioExtension visualStudioExtension = componentProject.getModelRegistry().get(ModelPath.path("visualStudio"), ModelType.of(VisualStudioExtension.class));
        VisualStudioProjectRegistry projectRegistry = ((VisualStudioExtensionInternal) visualStudioExtension).getProjectRegistry();
        return projectRegistry.getProjectConfiguration(nativeBinary);
    }

    private ProjectInternal getComponentProject(NativeBinarySpec nativeBinary) {
        String projectPath = nativeBinary.getComponent().getProjectPath();
        return projectLocator.locateProject(projectPath);
    }
}

