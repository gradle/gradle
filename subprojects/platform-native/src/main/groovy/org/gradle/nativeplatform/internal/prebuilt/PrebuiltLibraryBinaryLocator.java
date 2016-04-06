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
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.resolve.LibraryBinaryLocator;

public class PrebuiltLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectModelResolver projectModelResolver;

    public PrebuiltLibraryBinaryLocator(ProjectModelResolver projectModelResolver) {
        this.projectModelResolver = projectModelResolver;
    }

    @Override
    public DomainObjectSet<NativeLibraryBinary> getBinaries(NativeLibraryRequirement requirement) {
        ModelRegistry projectModel = projectModelResolver.resolveProjectModel(requirement.getProjectPath());
        NamedDomainObjectSet<PrebuiltLibraries> repositories = projectModel.realize("repositories", Repositories.class).withType(PrebuiltLibraries.class);
        if (repositories.isEmpty()) {
            return null;
        }
        PrebuiltLibrary prebuiltLibrary = getPrebuiltLibrary(repositories, requirement.getLibraryName());
        return prebuiltLibrary != null ? prebuiltLibrary.getBinaries() : null;
    }

    private PrebuiltLibrary getPrebuiltLibrary(NamedDomainObjectSet<PrebuiltLibraries> repositories, String libraryName) {
        for (PrebuiltLibraries prebuiltLibraries : repositories) {
            PrebuiltLibrary prebuiltLibrary = prebuiltLibraries.resolveLibrary(libraryName);
            if (prebuiltLibrary != null) {
                return prebuiltLibrary;
            }
        }
        return null;
    }
}
