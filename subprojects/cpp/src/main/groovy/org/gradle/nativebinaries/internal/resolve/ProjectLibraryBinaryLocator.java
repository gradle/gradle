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
package org.gradle.nativebinaries.internal.resolve;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Project;
import org.gradle.nativebinaries.LibraryContainer;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.NativeLibraryRequirement;

public class ProjectLibraryBinaryLocator implements LibraryBinaryLocator {
    private final ProjectLocator projectLocator;

    public ProjectLibraryBinaryLocator(ProjectLocator projectLocator) {
        this.projectLocator = projectLocator;
    }

    public DomainObjectSet<NativeBinary> getBinaries(NativeLibraryRequirement requirement) {
        Project project = findProject(requirement);
        LibraryContainer libraryContainer = (LibraryContainer) project.getExtensions().findByName("libraries");
        if (libraryContainer == null) {
            throw new LibraryResolveException(String.format("Project does not have a libraries container: '%s'", project.getPath()));
        }
        return libraryContainer.getByName(requirement.getLibraryName()).getBinaries();
    }

    private Project findProject(NativeLibraryRequirement requirement) {
        return projectLocator.locateProject(requirement.getProjectPath());
    }

}
