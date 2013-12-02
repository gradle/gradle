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

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.internal.NativeComponentInternal;

public class VisualStudioProjectResolver {
    private final ProjectInternal projectFinder;

    public VisualStudioProjectResolver(ProjectInternal projectFinder) {
        this.projectFinder = projectFinder;
    }

    public VisualStudioProjectConfiguration lookupProjectConfiguration(NativeBinary nativeBinary) {
        // Looks in the correct project registry for this binary
        Project componentProject = getComponentProject(nativeBinary);
        VisualStudioProjectRegistry projectRegistry = componentProject.getExtensions().getByType(VisualStudioProjectRegistry.class);
        return projectRegistry.getProjectConfiguration(nativeBinary);
    }

    private Project getComponentProject(NativeBinary nativeBinary) {
        String projectPath = ((NativeComponentInternal) nativeBinary.getComponent()).getProjectPath();
        return projectFinder.project(projectPath);
    }
}

