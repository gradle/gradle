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

package org.gradle.nativecode.base.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.nativecode.base.*;

import java.util.List;

public class CreateNativeBinariesAction implements Action<ProjectInternal> {
    private final Instantiator instantiator;

    public CreateNativeBinariesAction(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void execute(ProjectInternal project) {
        ToolChainRegistry toolChains = project.getExtensions().getByType(ToolChainRegistry.class);
        ExecutableContainer executables = project.getExtensions().getByType(ExecutableContainer.class);
        LibraryContainer libraries = project.getExtensions().getByType(LibraryContainer.class);
        BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);

        NativeBinaryFactory factory = new NativeBinaryFactory(instantiator, project, toolChains);
        List<? extends ToolChain> availableToolChains = toolChains.getAvailableToolChains();
        for (ToolChain toolChain : availableToolChains) {
            for (Library library : libraries) {
                for (Flavor flavor : library.getFlavors()) {
                    binaries.add(factory.createNativeBinary(DefaultSharedLibraryBinary.class, library, toolChain, flavor));
                    binaries.add(factory.createNativeBinary(DefaultStaticLibraryBinary.class, library, toolChain, flavor));
                }
            }
            for (Executable executable : executables) {
                for (Flavor flavor : executable.getFlavors()) {
                    binaries.add(factory.createNativeBinary(DefaultExecutableBinary.class, executable, toolChain, flavor));
                }
            }
        }
    }

}
