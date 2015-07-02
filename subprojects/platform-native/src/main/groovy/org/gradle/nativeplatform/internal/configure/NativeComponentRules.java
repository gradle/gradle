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

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.Finalize;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.platform.base.internal.*;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Cross cutting rules for all instances of {@link org.gradle.nativeplatform.NativeComponentSpec}
 */
@SuppressWarnings("UnusedDeclaration")
public class NativeComponentRules extends RuleSource {
    @Defaults
    public void applyHeaderSourceSetConventions(final NativeComponentSpec component) {
        component.getSources().withType(HeaderExportingSourceSet.class).afterEach(new Action<HeaderExportingSourceSet>() {
            @Override
            public void execute(HeaderExportingSourceSet headerSourceSet) {
                // Only apply default locations when none explicitly configured
                if (headerSourceSet.getExportedHeaders().getSrcDirs().isEmpty()) {
                    headerSourceSet.getExportedHeaders().srcDir(String.format("src/%s/headers", component.getName()));
                }

                headerSourceSet.getImplicitHeaders().setSrcDirs(headerSourceSet.getSource().getSrcDirs());
                headerSourceSet.getImplicitHeaders().include("**/*.h");
            }
        });
    }

    @Finalize
    public static void createBinaries(
        NativeComponentSpec nativeComponent,
        PlatformResolvers platforms,
        BuildTypeContainer buildTypes,
        FlavorContainer flavors,
        ServiceRegistry serviceRegistry
    ) {
        final NativePlatforms nativePlatforms = serviceRegistry.get(NativePlatforms.class);
        final NativeDependencyResolver nativeDependencyResolver = serviceRegistry.get(NativeDependencyResolver.class);

        createBinariesImpl(nativeComponent, platforms, buildTypes, flavors, nativePlatforms, nativeDependencyResolver, new DefaultBinaryNamingSchemeBuilder());
    }

    static void createBinariesImpl(
        NativeComponentSpec nativeComponent,
        PlatformResolvers platforms,
        Set<? extends BuildType> buildTypes,
        Set<? extends Flavor> flavors,
        NativePlatforms nativePlatforms,
        NativeDependencyResolver nativeDependencyResolver,
        BinaryNamingSchemeBuilder namingSchemeBuilder
    ) {
        if (!(nativeComponent instanceof TargetedNativeComponentInternal)) {
            return;
        }
        TargetedNativeComponentInternal targetedComponent = (TargetedNativeComponentInternal) nativeComponent;
        List<NativePlatform> resolvedPlatforms = resolvePlatforms(targetedComponent, nativePlatforms, platforms);

        for (NativePlatform platform : resolvedPlatforms) {
            BinaryNamingSchemeBuilder builder = namingSchemeBuilder.withComponentName(nativeComponent.getName());
            builder = maybeAddDimension(builder, resolvedPlatforms, platform.getName());
            executeForEachBuildType(
                nativeComponent,
                (NativePlatformInternal) platform,
                builder,
                buildTypes,
                flavors,
                nativeDependencyResolver
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

    private static BinaryNamingSchemeBuilder maybeAddDimension(BinaryNamingSchemeBuilder builder, Collection<?> variations, String name) {
        if (variations.size() > 1) {
            builder = builder.withVariantDimension(name);
        }
        return builder;
    }

    private static void executeForEachBuildType(
        NativeComponentSpec projectNativeComponent,
        NativePlatformInternal platform,
        BinaryNamingSchemeBuilder builder,
        Set<? extends BuildType> allBuildTypes,
        Set<? extends Flavor> allFlavors,
        NativeDependencyResolver nativeDependencyResolver
    ) {
        Set<BuildType> targetBuildTypes = ((TargetedNativeComponentInternal) projectNativeComponent).chooseBuildTypes(allBuildTypes);
        for (BuildType buildType : targetBuildTypes) {
            BinaryNamingSchemeBuilder nameBuilder = maybeAddDimension(builder, targetBuildTypes, buildType.getName());
            executeForEachFlavor(
                projectNativeComponent,
                platform,
                buildType,
                nameBuilder,
                allFlavors,
                nativeDependencyResolver
            );
        }
    }

    private static void executeForEachFlavor(
        NativeComponentSpec projectNativeComponent,
        NativePlatform platform,
        BuildType buildType,
        BinaryNamingSchemeBuilder buildTypedNameBuilder,
        Set<? extends Flavor> allFlavors,
        NativeDependencyResolver nativeDependencyResolver
    ) {
        Set<Flavor> targetFlavors = ((TargetedNativeComponentInternal) projectNativeComponent).chooseFlavors(allFlavors);
        for (Flavor flavor : targetFlavors) {
            BinaryNamingSchemeBuilder flavoredNameBuilder = maybeAddDimension(buildTypedNameBuilder, targetFlavors, flavor.getName());
            NativeBinaries.createNativeBinaries(
                projectNativeComponent,
                projectNativeComponent.getBinaries().withType(NativeBinarySpec.class),
                nativeDependencyResolver,
                flavoredNameBuilder,
                platform,
                buildType,
                flavor
            );
        }
    }
}
