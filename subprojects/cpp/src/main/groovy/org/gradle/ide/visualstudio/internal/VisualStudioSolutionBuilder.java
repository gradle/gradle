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
package org.gradle.ide.visualstudio.internal;

import org.gradle.nativebinaries.LibraryBinary;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.NativeDependencySet;
import org.gradle.nativebinaries.internal.LibraryNativeDependencySet;
import org.gradle.nativebinaries.internal.NativeBinaryInternal;

public class VisualStudioSolutionBuilder {
    private final VisualStudioProjectRegistry projectRegistry;

    public VisualStudioSolutionBuilder(VisualStudioProjectRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    public VisualStudioSolution createSolution(NativeBinary nativeBinary) {
        VisualStudioSolution solution = new VisualStudioSolution(solutionName(nativeBinary), (NativeBinaryInternal) nativeBinary);
        addNativeBinary(solution, nativeBinary);
        return solution;
    }

    private void addNativeBinary(VisualStudioSolution solution, NativeBinary nativeBinary) {
        VisualStudioProjectConfiguration projectConfiguration = projectRegistry.getProjectConfiguration(nativeBinary);
        solution.addProjectConfiguration(projectConfiguration);

        for (NativeDependencySet dep : nativeBinary.getLibs()) {
            if (dep instanceof LibraryNativeDependencySet) {
                LibraryBinary dependencyBinary = ((LibraryNativeDependencySet) dep).getLibraryBinary();
                addNativeBinary(solution, dependencyBinary);
            }
        }
    }

    private String solutionName(NativeBinary nativeBinary) {
        return nativeBinary.getComponent().getName();
    }
}
