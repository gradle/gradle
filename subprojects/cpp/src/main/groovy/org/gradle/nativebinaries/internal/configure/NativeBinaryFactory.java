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

import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.internal.DefaultBinaryNamingScheme;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

class NativeBinaryFactory implements Transformer<Collection<NativeBinary>, ProjectNativeComponent> {
    private final Instantiator instantiator;
    private final Project project;
    private final NativeDependencyResolver resolver;
    private final ToolChainRegistryInternal toolChainRegistry;
    private final Set<Platform> allPlatforms = new LinkedHashSet<Platform>();
    private final Set<BuildType> allBuildTypes = new LinkedHashSet<BuildType>();
    private final Set<Flavor> allFlavors = new LinkedHashSet<Flavor>();

    public NativeBinaryFactory(Instantiator instantiator, NativeDependencyResolver resolver, Project project, ToolChainRegistryInternal toolChainRegistry,
                               Collection<? extends Platform> allPlatforms, Collection<? extends BuildType> allBuildTypes, Collection<? extends Flavor> allFlavors) {
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.toolChainRegistry = toolChainRegistry;
        this.project = project;
        this.allPlatforms.addAll(allPlatforms);
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
    }

    public Collection<NativeBinary> transform(ProjectNativeComponent original) {
        return createNativeBinaries((ProjectNativeComponentInternal) original);
    }

    public Collection<NativeBinary> createNativeBinaries(ProjectNativeComponentInternal component) {
        Set<NativeBinary> componentBinaries = new LinkedHashSet<NativeBinary>();
         for (Platform platform : component.choosePlatforms(allPlatforms)) {
             ToolChain toolChain = toolChainRegistry.getForPlatform(platform);
             for (BuildType buildType : component.chooseBuildTypes(allBuildTypes)) {
                 for (Flavor flavor : component.chooseFlavors(allFlavors)) {
                     componentBinaries.addAll(createNativeBinaries(component, toolChain, platform, buildType, flavor));
                 }
             }
         }
        return componentBinaries;
    }

    public Collection<NativeBinary> createNativeBinaries(ProjectNativeComponentInternal component, ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        Collection<NativeBinary> binaries = new LinkedList<NativeBinary>();
        if (component instanceof Library) {
            binaries.add(createNativeBinary(ProjectSharedLibraryBinary.class, component, toolChain, platform, buildType, flavor));
            binaries.add(createNativeBinary(ProjectStaticLibraryBinary.class, component, toolChain, platform, buildType, flavor));
        } else {
            binaries.add(createNativeBinary(ProjectExecutableBinary.class, component, toolChain, platform, buildType, flavor));
        }
        return binaries;
    }

    public <T extends AbstractProjectNativeBinary> T createNativeBinary(Class<T> type, ProjectNativeComponentInternal component, ToolChain toolChain, Platform platform, BuildType buildType, Flavor flavor) {
        DefaultBinaryNamingScheme namingScheme = createNamingScheme(component, platform, buildType, flavor);
        T nativeBinary = instantiator.newInstance(type, component, flavor, toolChain, platform, buildType, namingScheme, resolver);
        setupDefaults(project, nativeBinary);
        component.getBinaries().add(nativeBinary);
        return nativeBinary;
    }

    private DefaultBinaryNamingScheme createNamingScheme(ProjectNativeComponentInternal component, Platform platform, BuildType buildType, Flavor flavor) {
        DefaultBinaryNamingScheme namingScheme = new DefaultBinaryNamingScheme(component.getName());
        if (usePlatformDimension(component)) {
            namingScheme = namingScheme.withVariantDimension(platform.getName());
        }
        if (useBuildTypeDimension(component)) {
            namingScheme = namingScheme.withVariantDimension(buildType.getName());
        }
        if (useFlavorDimension(component)) {
            namingScheme = namingScheme.withVariantDimension(flavor.getName());
        }
        return namingScheme;
    }

    private boolean usePlatformDimension(ProjectNativeComponentInternal component) {
        return component.choosePlatforms(allPlatforms).size() > 1;
    }

    private boolean useBuildTypeDimension(ProjectNativeComponentInternal component) {
        return component.chooseBuildTypes(allBuildTypes).size() > 1;
    }

    private boolean useFlavorDimension(ProjectNativeComponentInternal component) {
        return component.chooseFlavors(allFlavors).size() > 1;
    }

    private void setupDefaults(Project project, AbstractProjectNativeBinary nativeBinary) {
        nativeBinary.setOutputDir(new File(project.getBuildDir(), "binaries"));
    }
}
