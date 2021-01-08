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

import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.model.internal.registry.ModelRegistry;

public class VisualStudioProjectResolver {
    private final ProjectModelResolver projectModelResolver;

    public VisualStudioProjectResolver(ProjectModelResolver projectModelResolver) {
        this.projectModelResolver = projectModelResolver;
    }

    public VisualStudioProjectConfiguration lookupProjectConfiguration(VisualStudioTargetBinary nativeBinary) {
        // Looks in the correct project registry for this binary
        VisualStudioExtension visualStudioExtension = getComponentModel(nativeBinary).realize("visualStudio", VisualStudioExtension.class);
        VisualStudioProjectRegistry projectRegistry = ((VisualStudioExtensionInternal) visualStudioExtension).getProjectRegistry();
        return projectRegistry.getProjectConfiguration(nativeBinary);
    }

    private ModelRegistry getComponentModel(VisualStudioTargetBinary nativeBinary) {
        String projectPath = nativeBinary.getProjectPath();
        return projectModelResolver.resolveProjectModel(projectPath);
    }
}

