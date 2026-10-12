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
import org.gradle.model.internal.registry.ModelRegistry;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("deprecation")
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
    public DomainObjectSet<org.gradle.nativeplatform.NativeLibraryBinary> getBinaries(LibraryIdentifier libraryIdentifier) {
        ModelRegistry projectModel = projectModelResolver.resolveProjectModel(libraryIdentifier.getProjectPath());
        org.gradle.platform.base.ComponentSpecContainer components = projectModel.find("components", org.gradle.platform.base.ComponentSpecContainer.class);
        if (components == null) {
            return null;
        }
        String libraryName = libraryIdentifier.getLibraryName();
        org.gradle.nativeplatform.NativeLibrarySpec library = components.withType(org.gradle.nativeplatform.NativeLibrarySpec.class).get(libraryName);
        if (library == null) {
            return null;
        }
        org.gradle.model.ModelMap<org.gradle.nativeplatform.NativeBinarySpec> projectBinaries = library.getBinaries().withType(org.gradle.nativeplatform.NativeBinarySpec.class);
        DomainObjectSet<org.gradle.nativeplatform.NativeLibraryBinary> binaries = domainObjectCollectionFactory.newDomainObjectSet(org.gradle.nativeplatform.NativeLibraryBinary.class);
        for (org.gradle.nativeplatform.NativeBinarySpec nativeBinarySpec : projectBinaries.values()) {
            binaries.add((org.gradle.nativeplatform.NativeLibraryBinary) nativeBinarySpec);
        }
        return binaries;
    }
}
