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
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.NativeComponentInternal;
import org.gradle.nativebinaries.internal.ToolChainRegistryInternal;
import org.gradle.util.CollectionUtils;

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

    public void create(BinaryContainer binaries, ToolChainRegistryInternal toolChains, PlatformContainer platforms) {
        BuildTypeContainer buildTypes = project.getExtensions().getByType(BuildTypeContainer.class);

        NativeBinaryFactory factory = new NativeBinaryFactory(instantiator, project, platforms, buildTypes);
        for (NativeComponentInternal component : allComponents()) {
            for (Platform platform : getPlatforms(component, platforms)) {
                ToolChain toolChain = toolChains.getForPlatform(platform);
                for (BuildType buildType : getBuildTypes(component, buildTypes)) {
                    for (Flavor flavor : component.getFlavors()) {
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
        return CollectionUtils.filter(platforms, new Spec<Platform>() {
            public boolean isSatisfiedBy(Platform element) {
                return component.shouldTarget(element);
            }
        });
    }

    private Set<BuildType> getBuildTypes(NativeComponentInternal component, BuildTypeContainer buildTypes) {
        return buildTypes;
    }
}
