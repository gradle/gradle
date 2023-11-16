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
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.platform.base.ComponentSpecContainer;

import javax.annotation.Nullable;

public class ProjectLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectModelResolver projectModelResolver;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;

    public ProjectLibraryBinaryLocator(ProjectModelResolver projectModelResolver, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        this.projectModelResolver = projectModelResolver;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
    }

    // Converts the binaries of a project library into regular binary instances
    @Nullable
    @Override
    public DomainObjectSet<NativeLibraryBinary> getBinaries(LibraryIdentifier libraryIdentifier) {
        ModelRegistry projectModel = projectModelResolver.resolveProjectModel(libraryIdentifier.getProjectPath());
        ComponentSpecContainer components = projectModel.find("components", ComponentSpecContainer.class);
        if (components == null) {
            return null;
        }
        String libraryName = libraryIdentifier.getLibraryName();
        NativeLibrarySpec library = components.withType(NativeLibrarySpec.class).get(libraryName);
        if (library == null) {
            return null;
        }
        ModelMap<NativeBinarySpec> projectBinaries = library.getBinaries().withType(NativeBinarySpec.class);
        DomainObjectSet<NativeLibraryBinary> binaries = domainObjectCollectionFactory.newDomainObjectSet(NativeLibraryBinary.class);
        for (NativeBinarySpec nativeBinarySpec : projectBinaries.values()) {
            binaries.add((NativeLibraryBinary) nativeBinarySpec);
        }
        return binaries;
    }
}
