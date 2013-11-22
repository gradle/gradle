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

package org.gradle.ide.visualstudio.model
import org.gradle.language.DependentSourceSet
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.AbstractBuildableModelElement
import org.gradle.nativebinaries.*
import org.gradle.util.CollectionUtils

class VisualStudioProject extends AbstractBuildableModelElement {
    final String name
    final NativeComponent component
    final String uuid
    final Map<NativeBinary, VisualStudioProjectConfiguration> configurations = [:]

    VisualStudioProject(String name, NativeComponent component) {
        this.name = name
        this.component = component
        this.uuid = '{' + UUID.randomUUID().toString() + '}'
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

    List<Library> getLibraryDependencies() {
        def libraries = []
        component.source.withType(DependentSourceSet).each {
            it.libs.each { lib ->
                if (lib instanceof Library) {
                    libraries << lib
                } else if (lib instanceof LibraryBinary) {
                    libraries << lib.component
                }
            }
        }
        return libraries
    }

    VisualStudioProjectConfiguration addConfiguration(NativeBinary nativeBinary) {
        def configuration = configurations[nativeBinary]
        if (configuration == null) {
            configuration = new VisualStudioProjectConfiguration(this, nativeBinary, configurationType(nativeBinary))
            configurations[nativeBinary] = configuration

            // TODO:DAZ Add dependencies and sources from binary
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
