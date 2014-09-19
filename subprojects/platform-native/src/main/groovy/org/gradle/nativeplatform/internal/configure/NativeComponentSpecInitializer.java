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

package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.Action;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.ToolChainRegistryInternal;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class NativeComponentSpecInitializer implements Action<NativeComponentSpec> {
    private final NativeBinariesFactory factory;
    private final ToolChainRegistryInternal toolChainRegistry;
    private final Set<NativePlatform> allPlatforms = new LinkedHashSet<NativePlatform>();
    private final Set<BuildType> allBuildTypes = new LinkedHashSet<BuildType>();
    private final Set<Flavor> allFlavors = new LinkedHashSet<Flavor>();
    private final BinaryNamingSchemeBuilder namingSchemeBuilder;

    public NativeComponentSpecInitializer(NativeBinariesFactory factory, BinaryNamingSchemeBuilder namingSchemeBuilder, ToolChainRegistryInternal toolChainRegistry,
                                          Collection<? extends NativePlatform> allPlatforms, Collection<? extends BuildType> allBuildTypes, Collection<? extends Flavor> allFlavors) {
        this.factory = factory;
        this.namingSchemeBuilder = namingSchemeBuilder;
        this.toolChainRegistry = toolChainRegistry;
        this.allPlatforms.addAll(allPlatforms);
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
    }

    public void execute(NativeComponentSpec projectNativeComponent) {
        TargetedNativeComponentInternal targetedComponent = (TargetedNativeComponentInternal) projectNativeComponent;
        for (NativePlatform platform : targetedComponent.choosePlatforms(allPlatforms)) {
            NativePlatformInternal platformInternal = (NativePlatformInternal) platform;
            NativeToolChainInternal toolChain = toolChainRegistry.getForPlatform(platformInternal);
            PlatformToolProvider toolProvider = toolChain.select(platformInternal);
            for (BuildType buildType : targetedComponent.chooseBuildTypes(allBuildTypes)) {
                for (Flavor flavor : targetedComponent.chooseFlavors(allFlavors)) {
                    BinaryNamingSchemeBuilder namingScheme = initializeNamingScheme(targetedComponent, projectNativeComponent.getName(), platform, buildType, flavor);
                    factory.createNativeBinaries(projectNativeComponent, namingScheme, toolChain, toolProvider, platform, buildType, flavor);
                }
            }
        }
    }

    private BinaryNamingSchemeBuilder initializeNamingScheme(TargetedNativeComponentInternal component, String name, NativePlatform platform, BuildType buildType, Flavor flavor) {
        BinaryNamingSchemeBuilder builder = namingSchemeBuilder.withComponentName(name);
        if (usePlatformDimension(component)) {
            builder = builder.withVariantDimension(platform.getName());
        }
        if (useBuildTypeDimension(component)) {
            builder = builder.withVariantDimension(buildType.getName());
        }
        if (useFlavorDimension(component)) {
            builder = builder.withVariantDimension(flavor.getName());
        }
        return builder;
    }

    private boolean usePlatformDimension(TargetedNativeComponentInternal component) {
        return component.choosePlatforms(allPlatforms).size() > 1;
    }

    private boolean useBuildTypeDimension(TargetedNativeComponentInternal component) {
        return component.chooseBuildTypes(allBuildTypes).size() > 1;
    }

    private boolean useFlavorDimension(TargetedNativeComponentInternal component) {
        return component.chooseFlavors(allFlavors).size() > 1;
    }

}
