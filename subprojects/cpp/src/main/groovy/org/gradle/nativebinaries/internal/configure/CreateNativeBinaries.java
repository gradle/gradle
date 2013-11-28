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
import org.gradle.nativebinaries.internal.NativeComponentInternal;
import org.gradle.nativebinaries.internal.ToolChainRegistryInternal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CreateNativeBinaries extends ModelRule {
    private final Instantiator instantiator;
    private final ProjectInternal project;

    public CreateNativeBinaries(Instantiator instantiator, ProjectInternal project) {
        this.instantiator = instantiator;
        this.project = project;
    }

    public void create(BinaryContainer binaries, ToolChainRegistryInternal toolChains, PlatformContainer platforms,
                       BuildTypeContainer buildTypes, FlavorContainer flavors) {
        // TODO:DAZ Work out the right way to make these containers available to binaries.all
        project.getExtensions().add("platforms", platforms);
        project.getExtensions().add("buildTypes", buildTypes);
        project.getExtensions().add("flavors", flavors);

        NativeBinaryFactory factory = new NativeBinaryFactory(instantiator, project, platforms, buildTypes, flavors);
        for (NativeComponentInternal component : allComponents()) {
            for (Platform platform : getPlatforms(component, platforms)) {
                ToolChain toolChain = toolChains.getForPlatform(platform);
                for (BuildType buildType : getBuildTypes(component, buildTypes)) {
                    for (Flavor flavor : getFlavors(component, flavors)) {
                        binaries.addAll(factory.createNativeBinaries(component, toolChain, platform, buildType, flavor));
                    }
                }
            }
        }
    }

    private Collection<NativeComponentInternal> allComponents() {
        ExecutableContainer executables = project.getExtensions().getByType(ExecutableContainer.class);
        LibraryContainer libraries = project.getExtensions().getByType(LibraryContainer.class);

        List<NativeComponentInternal> components = new ArrayList<NativeComponentInternal>();
        for (Library library : libraries) {
            components.add((NativeComponentInternal) library);
        }
        for (Executable executable : executables) {
            components.add((NativeComponentInternal) executable);
        }
        return components;
    }

    private Set<Platform> getPlatforms(final NativeComponentInternal component, PlatformContainer platforms) {
        return component.choosePlatforms(platforms);
    }

    private Set<BuildType> getBuildTypes(final NativeComponentInternal component, BuildTypeContainer buildTypes) {
        return component.chooseBuildTypes(buildTypes);
    }

    // TODO:DAZ Maybe add NativeBinaryInternal.selectFlavors(FlavorContainer) >> Set<Flavor>
    private Set<Flavor> getFlavors(final NativeComponentInternal component, FlavorContainer flavors) {
        return component.chooseFlavors(flavors);
    }
}
