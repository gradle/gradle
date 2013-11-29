/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.tasks.Optional;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationParser;
import org.gradle.nativebinaries.*;

import java.util.Collection;

class NativeDependencyMapNotationParser extends MapNotationParser<NativeLibraryDependency> {

    private final ProjectFinder projectFinder;

    public NativeDependencyMapNotationParser(ProjectFinder projectFinder) {
        this.projectFinder = projectFinder;
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Map with mandatory 'library' and optional 'project' and 'linkage' keys, e.g. [project: ':someProj', library: 'mylib', linkage: 'static']");
    }

    @SuppressWarnings("unused")
    protected NativeLibraryDependency parseMap(@MapKey("library") String libraryName, @Optional @MapKey("project") String projectPath, @Optional @MapKey("linkage") String linkage) {
        return new ProjectLibraryDependency(projectFinder, projectPath, libraryName, getTypeForLinkage(linkage));
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

    private class ProjectLibraryDependency implements NativeLibraryDependency {
        private final ProjectFinder projectFinder;
        private final String projectPath;
        private final String libraryName;
        private final Class<? extends LibraryBinary> type;

        public ProjectLibraryDependency(ProjectFinder projectFinder, String projectPath, String libraryName, Class<? extends LibraryBinary> type) {
            this.projectFinder = projectFinder;
            this.projectPath = projectPath;
            this.libraryName = libraryName;
            this.type = type;
        }

        public Library getLibrary() {
            Project project = projectFinder.getProject(projectPath);
            LibraryContainer libraryContainer = (LibraryContainer) project.getExtensions().findByName("libraries");
            if (libraryContainer == null) {
                throw new InvalidUserDataException(String.format("Project does not have a libraries container: '%s'", project.getPath()));
            }
            return libraryContainer.getByName(libraryName);
        }

        public Class<? extends LibraryBinary> getType() {
            return type;
        }
    }
}
