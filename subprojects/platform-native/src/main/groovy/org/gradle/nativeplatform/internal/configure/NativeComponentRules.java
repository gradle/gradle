/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.platform.base.internal.*;
import org.gradle.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Cross cutting rules for all instances of {@link org.gradle.nativeplatform.NativeComponentSpec}
 */
public class NativeComponentRules {
    public static void createBinariesImpl(
        TargetedNativeComponentInternal nativeComponent,
        PlatformResolvers platforms,
        Set<? extends BuildType> buildTypes,
        Set<? extends Flavor> flavors,
        NativePlatforms nativePlatforms,
        NativeDependencyResolver nativeDependencyResolver,
        FileCollectionFactory fileCollectionFactory
    ) {
        List<NativePlatform> resolvedPlatforms = resolvePlatforms(nativeComponent, nativePlatforms, platforms);

        for (NativePlatform platform : resolvedPlatforms) {
            BinaryNamingScheme namingScheme = DefaultBinaryNamingScheme.component(nativeComponent.getName());
            namingScheme = namingScheme.withVariantDimension(platform, resolvedPlatforms);
            executeForEachBuildType(
                nativeComponent,
                (NativePlatformInternal) platform,
                namingScheme,
                buildTypes,
                flavors,
                nativeDependencyResolver,
                fileCollectionFactory
            );
        }
    }

    private static List<NativePlatform> resolvePlatforms(TargetedNativeComponentInternal targetedComponent, NativePlatforms nativePlatforms, final PlatformResolvers platforms) {
        List<PlatformRequirement> targetPlatforms = targetedComponent.getTargetPlatforms();
        if (targetPlatforms.isEmpty()) {
            PlatformRequirement requirement = DefaultPlatformRequirement.create(nativePlatforms.getDefaultPlatformName());
            targetPlatforms = Collections.singletonList(requirement);
        }
        return CollectionUtils.collect(targetPlatforms, new Transformer<NativePlatform, PlatformRequirement>() {
            @Override
            public NativePlatform transform(PlatformRequirement platformRequirement) {
                return platforms.resolve(NativePlatform.class, platformRequirement);
            }
        });
    }

    private static void executeForEachBuildType(
        TargetedNativeComponentInternal projectNativeComponent,
        NativePlatformInternal platform,
        BinaryNamingScheme namingScheme,
        Set<? extends BuildType> allBuildTypes,
        Set<? extends Flavor> allFlavors,
        NativeDependencyResolver nativeDependencyResolver,
        FileCollectionFactory fileCollectionFactory
    ) {
        Set<BuildType> targetBuildTypes = projectNativeComponent.chooseBuildTypes(allBuildTypes);
        for (BuildType buildType : targetBuildTypes) {
            BinaryNamingScheme namingSchemeWithBuildType = namingScheme.withVariantDimension(buildType, targetBuildTypes);
            executeForEachFlavor(
                projectNativeComponent,
                platform,
                buildType,
                namingSchemeWithBuildType,
                allFlavors,
                nativeDependencyResolver,
                fileCollectionFactory
            );
        }
    }

    private static void executeForEachFlavor(
        TargetedNativeComponentInternal projectNativeComponent,
        NativePlatform platform,
        BuildType buildType,
        BinaryNamingScheme namingScheme,
        Set<? extends Flavor> allFlavors,
        NativeDependencyResolver nativeDependencyResolver,
        FileCollectionFactory fileCollectionFactory
    ) {
        Set<Flavor> targetFlavors = projectNativeComponent.chooseFlavors(allFlavors);
        for (Flavor flavor : targetFlavors) {
            BinaryNamingScheme namingSchemeWithFlavor = namingScheme.withVariantDimension(flavor, targetFlavors);
            NativeBinaries.createNativeBinaries(
                projectNativeComponent,
                projectNativeComponent.getBinaries().withType(NativeBinarySpec.class),
                nativeDependencyResolver,
                fileCollectionFactory,
                namingSchemeWithFlavor,
                platform,
                buildType,
                flavor
            );
        }
    }
}
