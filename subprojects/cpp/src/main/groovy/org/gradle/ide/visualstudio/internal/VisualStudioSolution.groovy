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
import org.gradle.language.base.internal.AbstractBuildableModelElement
import org.gradle.nativebinaries.LibraryBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.NativeDependencySet
import org.gradle.nativebinaries.internal.NativeBinaryInternal
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet

class VisualStudioSolution extends AbstractBuildableModelElement implements Named {
    final String name
    private final NativeBinaryInternal rootBinary
    private final FileResolver fileResolver
    private final VisualStudioProjectResolver vsProjectResolver

    VisualStudioSolution(String name, NativeBinaryInternal rootBinary, FileResolver fileResolver, VisualStudioProjectResolver vsProjectResolver) {
        this.name = name
        this.rootBinary = rootBinary
        this.fileResolver = fileResolver
        this.vsProjectResolver = vsProjectResolver
    }

    String getComponentName() {
        return rootBinary.component.baseName
    }

    Set<VisualStudioProjectConfiguration> getProjectConfigurations() {
        def configurations = [] as Set
        addNativeBinary(configurations, rootBinary)
        return configurations
    }

    private void addNativeBinary(Set configurations, NativeBinary nativeBinary) {
        VisualStudioProjectConfiguration projectConfiguration = vsProjectResolver.lookupProjectConfiguration(nativeBinary);
        configurations.add(projectConfiguration)

        for (NativeDependencySet dep : nativeBinary.getLibs()) {
            if (dep instanceof LibraryNativeDependencySet) {
                LibraryBinary dependencyBinary = ((LibraryNativeDependencySet) dep).getLibraryBinary();
                addNativeBinary(configurations, dependencyBinary)
            }
        }
    }

    File getSolutionFile() {
        return fileResolver.resolve("visualStudio/${name}.sln")
    }
}
