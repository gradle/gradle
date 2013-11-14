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

import org.gradle.api.DomainObjectSet
import org.gradle.language.DependentSourceSet
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.nativebinaries.Library
import org.gradle.nativebinaries.LibraryBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.NativeComponent

abstract class VisualStudioProject {
    final NativeComponent component
    private final String uuid
    private final String nameSuffix

    VisualStudioProject(NativeComponent component, String nameSuffix) {
        this.component = component
        this.nameSuffix = nameSuffix
        this.uuid = '{' + UUID.randomUUID().toString() + '}'
    }

    String getName() {
        return "${component.name.capitalize()}${nameSuffix}"
    }

    String getUuid() {
        return uuid
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

    protected DomainObjectSet<NativeBinary> getCandidateBinaries() {
        return component.binaries
    }

    abstract List<? extends VisualStudioProjectConfiguration> getConfigurations()
}
