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
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibraries;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.Repositories;
import org.gradle.nativeplatform.internal.resolve.LibraryBinaryLocator;
import org.gradle.nativeplatform.internal.resolve.LibraryIdentifier;

import javax.annotation.Nullable;

public class PrebuiltLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectModelResolver projectModelResolver;

    public PrebuiltLibraryBinaryLocator(ProjectModelResolver projectModelResolver) {
        this.projectModelResolver = projectModelResolver;
    }

    @Nullable
    @Override
    public DomainObjectSet<NativeLibraryBinary> getBinaries(LibraryIdentifier library) {
        ModelRegistry projectModel = projectModelResolver.resolveProjectModel(library.getProjectPath());
        Repositories repositories = projectModel.find("repositories", Repositories.class);
        if (repositories == null) {
            return null;
        }
        PrebuiltLibrary prebuiltLibrary = getPrebuiltLibrary(repositories.withType(PrebuiltLibraries.class), library.getLibraryName());
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
