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

import org.apache.commons.lang.StringUtils;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.LibraryNativeDependencySet;
import org.gradle.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VisualStudioProjectRegistry {
    private Map<String, VisualStudioProject> projects = new HashMap<String, VisualStudioProject>();

    public VisualStudioProjectConfiguration getProjectConfiguration(NativeBinary nativeBinary) {
        VisualStudioProject vsProject = getProject(nativeBinary);

        for (NativeDependencySet dep : nativeBinary.getLibs()) {
            if (dep instanceof LibraryNativeDependencySet) {
                LibraryBinary dependencyBinary = ((LibraryNativeDependencySet) dep).getLibraryBinary();
                vsProject.addProjectReference(projectName(dependencyBinary));
            }
        }
        // TODO:DAZ Not sure if adding these on demand is sufficient
        return vsProject.addConfiguration(nativeBinary);
    }

    private VisualStudioProject getProject(NativeBinary nativeBinary) {
        String projectName = projectName(nativeBinary);
        VisualStudioProject vsProject = projects.get(projectName);
        if (vsProject == null) {
            vsProject = new VisualStudioProject(projectName, nativeBinary.getComponent());
            projects.put(projectName, vsProject);
        }
        return vsProject;
    }

    public VisualStudioProject getProject(String name) {
        return projects.get(name);
    }

    public List<VisualStudioProject> getAllProjects() {
        return CollectionUtils.toList(projects.values());
    }

    private static String projectName(NativeBinary nativeBinary) {
        return projectBaseName(nativeBinary) + projectSuffix(nativeBinary);
    }

    private static String projectSuffix(NativeBinary nativeBinary) {
        return nativeBinary instanceof StaticLibraryBinary ? "Lib"
                : nativeBinary instanceof SharedLibraryBinary ? "Dll"
                : "Exe";
    }

    // TODO:DAZ This needs to be unique for multi-project
    private static String projectBaseName(NativeBinary nativeBinary) {
        NativeComponent component = nativeBinary.getComponent();
        if (component.getFlavors().size() <= 1) {
            return component.getBaseName();
        }
        return nativeBinary.getFlavor().getName() + StringUtils.capitalize(component.getBaseName());
    }
}
