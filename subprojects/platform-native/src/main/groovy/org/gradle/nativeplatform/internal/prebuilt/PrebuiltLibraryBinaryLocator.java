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

package org.gradle.nativeplatform.internal.prebuilt;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelType;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.resolve.LibraryBinaryLocator;
import org.gradle.nativeplatform.internal.resolve.ProjectLocator;

import java.util.ArrayList;
import java.util.List;

public class PrebuiltLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectLocator projectLocator;

    public PrebuiltLibraryBinaryLocator(ProjectLocator projectLocator) {
        this.projectLocator = projectLocator;
    }

    public DomainObjectSet<NativeLibraryBinary> getBinaries(NativeLibraryRequirement requirement) {
        ProjectInternal project = projectLocator.locateProject(requirement.getProjectPath());
        NamedDomainObjectSet<PrebuiltLibraries> repositories = project.getModelRegistry().get(ModelPath.path("repositories"), ModelType.of(Repositories.class)).withType(PrebuiltLibraries.class);
        if (repositories.isEmpty()) {
            throw new PrebuiltLibraryResolveException("Project does not have any prebuilt library repositories.");
        }
        PrebuiltLibrary prebuiltLibrary = getPrebuiltLibrary(repositories, requirement.getLibraryName());
        return prebuiltLibrary.getBinaries();
    }

    private PrebuiltLibrary getPrebuiltLibrary(NamedDomainObjectSet<PrebuiltLibraries> repositories, String libraryName) {
        List<String> repositoryNames = new ArrayList<String>();
        for (PrebuiltLibraries prebuiltLibraries : repositories) {
            repositoryNames.add(prebuiltLibraries.getName());
            PrebuiltLibrary prebuiltLibrary = prebuiltLibraries.resolveLibrary(libraryName);
            if (prebuiltLibrary != null) {
                return prebuiltLibrary;
            }
        }
        throw new PrebuiltLibraryResolveException(
                String.format("Prebuilt library with name '%s' not found in repositories '%s'.", libraryName, repositoryNames));
    }
}
