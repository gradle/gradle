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
package org.gradle.nativeplatform.internal.resolve;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.DefaultModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryRequirement;
import org.gradle.nativeplatform.NativeLibrarySpec;

public class ProjectLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectLocator projectLocator;

    public ProjectLibraryBinaryLocator(ProjectLocator projectLocator) {
        this.projectLocator = projectLocator;
    }

    // Converts the binaries of a project library into regular binary instances
    public DomainObjectSet<NativeLibraryBinary> getBinaries(NativeLibraryRequirement requirement) {
        ProjectInternal project = findProject(requirement);
        ModelRegistry modelRegistry = project.getModelRegistry();
        ModelMap<NativeLibrarySpec> nativeLibraries = modelRegistry.find(ModelPath.path("components"), DefaultModelMap.modelMapTypeOf(NativeLibrarySpec.class));
        if (nativeLibraries == null) {
            throw new LibraryResolveException(String.format("Project does not have a libraries container: '%s'", project.getPath()));
        }
        String libraryName = requirement.getLibraryName();
        if (!nativeLibraries.containsKey(libraryName)) {
            throw new UnknownDomainObjectException(String.format("%s with name '%s' not found.", NativeLibrarySpec.class.getSimpleName(), libraryName));
        }
        NamedDomainObjectCollection<NativeBinarySpec> projectBinaries = nativeLibraries.get(libraryName).getBinaries().withType(NativeBinarySpec.class);
        DomainObjectSet<NativeLibraryBinary> binaries = new DefaultDomainObjectSet<NativeLibraryBinary>(NativeLibraryBinary.class);
        // TODO:DAZ Convert, don't cast
        for (NativeBinarySpec nativeBinarySpec : projectBinaries) {
            binaries.add((NativeLibraryBinary) nativeBinarySpec);
        }
        return binaries;
    }

    private ProjectInternal findProject(NativeLibraryRequirement requirement) {
        return projectLocator.locateProject(requirement.getProjectPath());
    }

}
