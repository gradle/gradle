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
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryRequirement;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecContainer;

public class ProjectLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectLocator projectLocator;
    private final Instantiator instantiator;

    public ProjectLibraryBinaryLocator(ProjectLocator projectLocator, Instantiator instantiator) {
        this.projectLocator = projectLocator;
        this.instantiator = instantiator;
    }

    // Converts the binaries of a project library into regular binary instances
    public  DomainObjectSet<NativeLibraryBinary> getBinaries(NativeLibraryRequirement requirement) {
        Project project = findProject(requirement);
        ComponentSpecContainer componentSpecContainer = project.getExtensions().findByType(ComponentSpecContainer.class);
        if (componentSpecContainer == null) {
            throw new LibraryResolveException(String.format("Project does not have a libraries container: '%s'", project.getPath()));
        }
        DomainObjectSet<NativeBinarySpec> projectBinaries = componentSpecContainer.withType(NativeLibrarySpec.class).getByName(requirement.getLibraryName()).getBinaries();
        DomainObjectSet<NativeLibraryBinary> binaries = new  DefaultDomainObjectSet<NativeLibraryBinary>(NativeLibraryBinary.class);
        BinaryContainer binaryContainer = project.getExtensions().findByType(BinaryContainer.class);
        //TODO:DAZ Convert, don't cast
        for (NativeBinarySpec nativeBinarySpec : projectBinaries) {
            //TODO freekh: This is probably not exactly what we want, but the way the BinaryContainer is used throughout the current model forces us. Ask DAZ to make sure.
            //Lookup using binary container (similar named binaries might might be removed)
            NativeLibraryBinary binary = (NativeLibraryBinary) binaryContainer.findByName(nativeBinarySpec.getName());
            if (binary != null) {
                binaries.add(binary);
            }
        }
        return binaries;
    }

    private Project findProject(NativeLibraryRequirement requirement) {
        return projectLocator.locateProject(requirement.getProjectPath());
    }

}
