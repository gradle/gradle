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
import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.ide.visualstudio.TextConfigFile
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.VisualStudioSolution
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.internal.AbstractBuildableModelElement
import org.gradle.nativebinaries.LibraryBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.NativeComponent
import org.gradle.nativebinaries.NativeDependencySet
import org.gradle.nativebinaries.internal.NativeBinaryInternal
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet

class DefaultVisualStudioSolution extends AbstractBuildableModelElement implements VisualStudioSolution {
    final String name
    final String configurationName
    final SolutionFile solutionFile
    private final NativeBinaryInternal rootBinary
    private final VisualStudioProjectResolver vsProjectResolver

    DefaultVisualStudioSolution(VisualStudioProjectConfiguration rootProjectConfiguration, NativeBinaryInternal rootBinary, FileResolver fileResolver,
                                VisualStudioProjectResolver vsProjectResolver, Instantiator instantiator) {
        this.name = rootProjectConfiguration.project.name
        this.configurationName = rootProjectConfiguration.name
        this.rootBinary = rootBinary
        this.vsProjectResolver = vsProjectResolver
        this.solutionFile = instantiator.newInstance(SolutionFile, fileResolver, "visualStudio/${name}.sln" as String)
    }

    NativeComponent getComponent() {
        return rootBinary.component
    }

    Set<VisualStudioProject> getProjects() {
        return getProjectConfigurations().collect {it.project} as Set
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

    static class SolutionFile implements TextConfigFile {
        private final List<Action<? super StringBuilder>> actions = new ArrayList<Action<? super StringBuilder>>();
        private final FileResolver fileResolver
        private Object location

        SolutionFile(FileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver
            this.location = defaultLocation
        }

        File getLocation() {
            return fileResolver.resolve(location)
        }

        void setLocation(Object location) {
            this.location = location
        }

        void withText(Action<? super StringBuilder> action) {
            actions.add(action)
        }

        List<Action<? super StringBuilder>> getTextActions() {
            return actions
        }
    }
}
