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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.nativebinaries.*;

import java.util.Set;

class LibraryBinaryLocator {
    private final ProjectFinder projectFinder;

    LibraryBinaryLocator(ProjectFinder projectFinder) {
        this.projectFinder = projectFinder;
    }

    public Set<? extends LibraryBinary> getCandidateBinaries(NativeLibraryRequirement requirement) {
        Library library = findLibrary(requirement);
        Class<? extends LibraryBinary> type = getTypeForLinkage(requirement.getLinkage());
        return library.getBinaries().withType(type);
    }

    private Project findProject(NativeLibraryRequirement requirement) {
        return projectFinder.getProject(requirement.getProjectPath());
    }

    private Library findLibrary(NativeLibraryRequirement requirement) {
        Project project = findProject(requirement);
        LibraryContainer libraryContainer = (LibraryContainer) project.getExtensions().findByName("libraries");
        if (libraryContainer == null) {
            throw new InvalidUserDataException(String.format("Project does not have a libraries container: '%s'", project.getPath()));
        }
        return libraryContainer.getByName(requirement.getLibraryName());
    }

    private Class<? extends LibraryBinary> getTypeForLinkage(String linkage) {
        if ("static".equals(linkage)) {
            return StaticLibraryBinary.class;
        }
        if ("shared".equals(linkage) || linkage == null) {
            return SharedLibraryBinary.class;
        }
        throw new InvalidUserDataException("Not a valid linkage: " + linkage);
    }
}
