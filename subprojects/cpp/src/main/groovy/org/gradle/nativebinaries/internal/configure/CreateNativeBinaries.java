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

package org.gradle.nativebinaries.internal.configure;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.DefaultExecutableBinary;
import org.gradle.nativebinaries.internal.DefaultSharedLibraryBinary;
import org.gradle.nativebinaries.internal.DefaultStaticLibraryBinary;
import org.gradle.nativebinaries.internal.ToolChainRegistryInternal;

public class CreateNativeBinaries extends ModelRule {
    private final Instantiator instantiator;
    private final ProjectInternal project;

    public CreateNativeBinaries(Instantiator instantiator, ProjectInternal project) {
        this.instantiator = instantiator;
        this.project = project;
    }

    public void create(BinaryContainer binaries, ToolChainRegistryInternal toolChains, PlatformContainer platforms) {
        BuildTypeContainer buildTypes = project.getExtensions().getByType(BuildTypeContainer.class);
        ExecutableContainer executables = project.getExtensions().getByType(ExecutableContainer.class);
        LibraryContainer libraries = project.getExtensions().getByType(LibraryContainer.class);

        NativeBinaryFactory factory = new NativeBinaryFactory(instantiator, project, platforms, buildTypes);
        for (Platform targetPlatform : platforms) {
            ToolChain toolChain = toolChains.getForPlatform(targetPlatform);
            for (BuildType buildType : buildTypes) {
                for (Library library : libraries) {
                    for (Flavor flavor : library.getFlavors()) {
                        binaries.add(factory.createNativeBinary(DefaultSharedLibraryBinary.class, library, toolChain, targetPlatform, buildType, flavor));
                        binaries.add(factory.createNativeBinary(DefaultStaticLibraryBinary.class, library, toolChain, targetPlatform, buildType, flavor));
                    }
                }
                for (Executable executable : executables) {
                    for (Flavor flavor : executable.getFlavors()) {
                        binaries.add(factory.createNativeBinary(DefaultExecutableBinary.class, executable, toolChain, targetPlatform, buildType, flavor));
                    }
                }
            }
        }
    }
}
