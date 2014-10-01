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
import org.gradle.api.Named;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NativeComponentSpecInitializer implements Action<NativeComponentSpec> { //TODO: move to platform.base too?
    private final NativeBinariesFactory factory;
    private final NativeToolChainRegistryInternal toolChainRegistry;
    private final PlatformContainer platforms;
    private final Set<BuildType> allBuildTypes = new LinkedHashSet<BuildType>();
    private final Set<Flavor> allFlavors = new LinkedHashSet<Flavor>();
    private final BinaryNamingSchemeBuilder namingSchemeBuilder;

    public NativeComponentSpecInitializer(NativeBinariesFactory factory, BinaryNamingSchemeBuilder namingSchemeBuilder, NativeToolChainRegistryInternal toolChainRegistry,
                                          PlatformContainer platforms, Collection<? extends BuildType> allBuildTypes, Collection<? extends Flavor> allFlavors) {
        this.factory = factory;
        this.namingSchemeBuilder = namingSchemeBuilder;
        this.toolChainRegistry = toolChainRegistry;
        this.allBuildTypes.addAll(allBuildTypes);
        this.allFlavors.addAll(allFlavors);
        this.platforms = platforms;
    }

    public void execute(NativeComponentSpec projectNativeComponent) {
        TargetedNativeComponentInternal targetedComponent = (TargetedNativeComponentInternal) projectNativeComponent;
        List<NativePlatform> targetPlatforms = platforms.select(NativePlatform.class, targetedComponent.getTargetPlatforms());

        for (NativePlatform platform: targetPlatforms) {
            NativeToolChainInternal toolChain = (NativeToolChainInternal) toolChainRegistry.getForPlatform(platform);
            PlatformToolProvider toolProvider = toolChain.select((NativePlatformInternal) platform);

            BinaryNamingSchemeBuilder builder = namingSchemeBuilder.withComponentName(projectNativeComponent.getName());
            builder = maybeAddDimension(builder, platform, targetPlatforms);
            executeForEachBuildType(projectNativeComponent, (NativePlatformInternal) platform, builder, toolChain, toolProvider);
        }
    }

    private void executeForEachBuildType(NativeComponentSpec projectNativeComponent, NativePlatformInternal platform, BinaryNamingSchemeBuilder builder, NativeToolChainInternal toolChain, PlatformToolProvider toolProvider) {
        Set<BuildType> targetBuildTypes = ((TargetedNativeComponentInternal) projectNativeComponent).chooseBuildTypes(allBuildTypes);
        for (BuildType buildType : targetBuildTypes) {
            BinaryNamingSchemeBuilder nameBuilder = maybeAddDimension(builder, buildType, targetBuildTypes);
            executeForEachFlavor(projectNativeComponent, platform, buildType, nameBuilder, toolChain, toolProvider);
        }
    }

    private void executeForEachFlavor(NativeComponentSpec projectNativeComponent, NativePlatform platform, BuildType buildType, BinaryNamingSchemeBuilder buildTypedNameBuilder, NativeToolChainInternal toolChain, PlatformToolProvider toolProvider) {
        Set<Flavor> targetFlavors = ((TargetedNativeComponentInternal) projectNativeComponent).chooseFlavors(allFlavors);
        for (Flavor flavor : targetFlavors) {
            BinaryNamingSchemeBuilder flavoredNameBuilder = maybeAddDimension(buildTypedNameBuilder, flavor, targetFlavors);
            factory.createNativeBinaries(projectNativeComponent, flavoredNameBuilder, toolChain, toolProvider, platform, buildType, flavor);
        }
    }

    private <T extends Named> BinaryNamingSchemeBuilder maybeAddDimension(BinaryNamingSchemeBuilder builder, T variation, Collection<T> variations) {
        if (variations.size() > 1) {
            builder = builder.withVariantDimension(variation.getName());
        }
        return builder;
    }
}
