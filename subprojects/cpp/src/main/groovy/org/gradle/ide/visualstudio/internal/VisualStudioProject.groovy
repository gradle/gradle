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
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.AbstractBuildableModelElement
import org.gradle.nativebinaries.*
import org.gradle.util.CollectionUtils
/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
// TODO:DAZ Sources and header files should be taken from all binaries added to project
class VisualStudioProject extends AbstractBuildableModelElement {
    final String uuid
    final String name
    final NativeComponent component
    final Map<NativeBinary, VisualStudioProjectConfiguration> configurations = [:]
    final Set<String> projectReferences = []

    VisualStudioProject(String name, NativeComponent component) {
        this.name = name
        this.component = component
        this.uuid = '{' + UUID.randomUUID().toString().toUpperCase() + '}'
    }

    String getProjectFile() {
        return "${name}.vcxproj"
    }

    String getFiltersFile() {
        return "${name}.vcxproj.filters"
    }

    List<File> getSourceFiles() {
        def allSource = []
        component.source.each { LanguageSourceSet sourceSet ->
            allSource.addAll sourceSet.source.files
        }
        return allSource
    }

    List<File> getHeaderFiles() {
        def allHeaders = []
        component.source.withType(HeaderExportingSourceSet).each { HeaderExportingSourceSet sourceSet ->
            allHeaders.addAll sourceSet.exportedHeaders.files
        }
        return allHeaders
    }

    void addProjectReference(String projectName) {
        projectReferences << projectName
    }

    VisualStudioProjectConfiguration addConfiguration(NativeBinary nativeBinary) {
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

    private static String configurationType(NativeBinary nativeBinary) {
        return nativeBinary instanceof StaticLibraryBinary ? "StaticLibrary" :
               nativeBinary instanceof SharedLibraryBinary ? "DynamicLibrary" :
               "Application"
    }
}
