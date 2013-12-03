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

package org.gradle.ide.visualstudio.internal
import org.gradle.api.Named
import org.gradle.api.internal.file.FileResolver
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.AbstractBuildableModelElement
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.NativeComponentInternal
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet
import org.gradle.util.CollectionUtils
/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
class VisualStudioProject extends AbstractBuildableModelElement implements Named {
    final VisualStudioProjectResolver projectResolver
    final FileResolver fileResolver
    final String name
    final NativeComponent component
    final Map<NativeBinary, VisualStudioProjectConfiguration> configurations = [:]

    VisualStudioProject(String name, NativeComponent component, FileResolver fileResolver, VisualStudioProjectResolver projectResolver) {
        this.fileResolver = fileResolver
        this.name = name
        this.component = component
        this.projectResolver = projectResolver
    }

    File getProjectFile() {
        return fileResolver.resolve("visualStudio/${name}.vcxproj")
    }

    String getUuid() {
        String projectPath = (component as NativeComponentInternal).projectPath
        String vsComponentPath = "${projectPath}:${name}"
        return '{' + UUID.nameUUIDFromBytes(vsComponentPath.bytes).toString().toUpperCase() + '}'
    }

    File getFiltersFile() {
        return fileResolver.resolve("visualStudio/${name}.vcxproj.filters")
    }

    List<File> getSourceFiles() {
        def allSource = [] as Set
        component.source.each { LanguageSourceSet sourceSet ->
            allSource.addAll sourceSet.source.files
        }
        return allSource as List
    }

    List<File> getHeaderFiles() {
        def allHeaders = [] as Set
        component.source.withType(HeaderExportingSourceSet).each { HeaderExportingSourceSet sourceSet ->
            allHeaders.addAll sourceSet.exportedHeaders.files
        }
        return allHeaders as List
    }

    Set<VisualStudioProject> getProjectReferences() {
        def projects = [] as Set
        component.binaries.each { NativeBinary binary ->
            binary.libs.each { NativeDependencySet dependencySet ->
                if (dependencySet instanceof LibraryNativeDependencySet) {
                    LibraryBinary dependencyBinary = ((LibraryNativeDependencySet) dependencySet).getLibraryBinary()
                    projects << projectResolver.lookupProjectConfiguration(dependencyBinary).getProject()
               }
            }
        }
        return projects
    }

    VisualStudioProjectConfiguration addConfiguration(NativeBinary nativeBinary) {
        // Assumes that all binaries added in this way will have the same sources and dependencies
        def configuration = configurations[nativeBinary]
        if (configuration == null) {
            configuration = new VisualStudioProjectConfiguration(this, nativeBinary, configurationType(nativeBinary))
            configurations[nativeBinary] = configuration
        }
        return configuration
    }

    List<VisualStudioProjectConfiguration> getConfigurations() {
        return CollectionUtils.toList(configurations.values())
    }

    VisualStudioProjectConfiguration getConfiguration(NativeBinary nativeBinary) {
        return configurations[nativeBinary]
    }

    private static String configurationType(NativeBinary nativeBinary) {
        return nativeBinary instanceof StaticLibraryBinary ? "StaticLibrary" :
               nativeBinary instanceof SharedLibraryBinary ? "DynamicLibrary" :
               "Application"
    }
}
