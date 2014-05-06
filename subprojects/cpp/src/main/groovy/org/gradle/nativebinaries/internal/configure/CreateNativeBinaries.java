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

import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.BuildTypeContainer;
import org.gradle.nativebinaries.FlavorContainer;
import org.gradle.nativebinaries.ProjectNativeBinary;
import org.gradle.nativebinaries.ProjectNativeComponent;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.PlatformContainer;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.SoftwareComponentContainer;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;

public class CreateNativeBinaries extends ModelRule {
    private final Instantiator instantiator;
    private final ProjectInternal project;
    private final NativeDependencyResolver resolver;

    public CreateNativeBinaries(Instantiator instantiator, ProjectInternal project, NativeDependencyResolver resolver) {
        this.instantiator = instantiator;
        this.project = project;
        this.resolver = resolver;
    }

    public void create(BinaryContainer binaries, ToolChainRegistryInternal toolChains,
                       PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors) {
        project.getExtensions().add("platforms", platforms);
        project.getExtensions().add("buildTypes", buildTypes);
        project.getExtensions().add("flavors", flavors);

        Action<ProjectNativeBinary> configureBinaryAction = new ProjectNativeBinaryInitializer(project);
        NativeBinariesFactory factory = new DefaultNativeBinariesFactory(instantiator, configureBinaryAction, resolver);
        BinaryNamingSchemeBuilder namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder();
        Action<ProjectNativeComponent> createBinariesAction =
                new ProjectNativeComponentInitializer(factory, namingSchemeBuilder, toolChains, platforms, buildTypes, flavors);

        SoftwareComponentContainer softwareComponents = project.getExtensions().getByType(SoftwareComponentContainer.class);
        for (ProjectNativeComponent component : softwareComponents.withType(ProjectNativeComponent.class)) {
            createBinariesAction.execute(component);
            binaries.addAll(component.getBinaries());
        }
    }
}
