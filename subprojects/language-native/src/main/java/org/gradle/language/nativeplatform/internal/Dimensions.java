/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.internal.DefaultUsageContext;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;
import static org.gradle.nativeplatform.MachineArchitecture.ARCHITECTURE_ATTRIBUTE;
import static org.gradle.nativeplatform.OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE;

public class Dimensions {
    public static String createDimensionSuffix(Named dimensionValue, Collection<?> multivalueProperty) {
        return createDimensionSuffix(dimensionValue.getName(), multivalueProperty);
    }

    public static String createDimensionSuffix(String dimensionValue, Collection<?> multivalueProperty) {
        if (isDimensionVisible(multivalueProperty)) {
            return StringUtils.capitalize(dimensionValue.toLowerCase());
        }
        return "";
    }

    public static String createDimensionSuffix(Optional<? extends Named> dimensionValue, Collection<?> multivalueProperty) {
        if (dimensionValue.isPresent() && isDimensionVisible(multivalueProperty)) {
            return StringUtils.capitalize(dimensionValue.get().getName().toLowerCase());
        }
        return "";
    }

    public static boolean isDimensionVisible(Collection<?> multivalueProperty) {
        return multivalueProperty.size() > 1;
    }

    public static <I> void variants(I component, Project project, ImmutableAttributesFactory attributesFactory, Action<NativeVariantIdentity> action) {
        Provider<String> baseName = Providers.notDefined();
        Collection<BuildType> buildTypes = getBuildTypes(component);
        Provider<Collection<Linkage>> linkageProvider = getAndFinalizeLinkages(component).map(validateLinkages());
        Provider<Collection<TargetMachine>> targetMachineProvider = getAndFinalizeTargetMachines(component).map(validateTargetMachines(component));
        Provider<String> baseNameProvider = getBaseName(component);

        variants(buildTypes, linkageProvider, targetMachineProvider, baseNameProvider, project, attributesFactory, action);
    }

    private static <I> Transformer<Collection<TargetMachine>, Collection<TargetMachine>> validateTargetMachines(I component) {
        return new Transformer<Collection<TargetMachine>, Collection<TargetMachine>>() {
            @Override
            public Collection<TargetMachine> transform(Collection<TargetMachine> values) {
                if (values.isEmpty()) {
                    String componentName = "library";
                    if (component instanceof CppApplication || component instanceof SwiftApplication) {
                        componentName = "application";
                    }
                    throw new IllegalArgumentException(String.format("A target machine needs to be specified for the %s.", componentName));
                }
                return values;
            }
        };
    }

    private static Transformer<Collection<Linkage>, Collection<Linkage>> validateLinkages() {
        return new Transformer<Collection<Linkage>, Collection<Linkage>>() {
            @Override
            public Collection<Linkage> transform(Collection<Linkage> values) {
                if (values.isEmpty()) {
                    throw new IllegalArgumentException("A linkage needs to be specified for the library.");
                }

                return values;
            }
        };
    }

    private static <I> Provider<? extends Collection<TargetMachine>> getAndFinalizeTargetMachines(I component) {
        if (component instanceof CppComponent) {
            ((CppComponent) component).getTargetMachines().finalizeValue();
            return ((CppComponent) component).getTargetMachines();
        } else if (component instanceof SwiftComponent) {
            ((SwiftComponent) component).getTargetMachines().finalizeValue();
            return ((SwiftComponent) component).getTargetMachines();
        }
        throw new IllegalArgumentException("No supported");
    }

    private static <I> Provider<? extends Collection<Linkage>> getAndFinalizeLinkages(I component) {
        if (component instanceof CppLibrary) {
            ((CppLibrary) component).getLinkage().finalizeValue();
            return ((CppLibrary) component).getLinkage();
        } else if (component instanceof SwiftLibrary) {
            ((SwiftLibrary) component).getLinkage().finalizeValue();
            return ((SwiftLibrary) component).getLinkage();
        }
        return Providers.notDefined();
    }

    private static <I> Provider<String> getBaseName(I component) {
        if (component instanceof CppComponent) {
            return ((CppComponent) component).getBaseName();
        } else if (component instanceof SwiftComponent){
            return ((SwiftComponent) component).getModule();
        }
        throw new UnsupportedOperationException();
    }

    private static <I> Collection<BuildType> getBuildTypes(I component) {
        if (component instanceof SwiftApplication || component instanceof SwiftLibrary || component instanceof CppApplication || component instanceof CppLibrary) {
            return BuildType.DEFAULT_BUILD_TYPES;
        }
        return Collections.singletonList(BuildType.DEBUG);
    }

    private static void variants(Collection<BuildType> buildTypes, Provider<Collection<Linkage>> linkageProvider, Provider<Collection<TargetMachine>> targetMachineProvider, Provider<String> baseName, Project project, ImmutableAttributesFactory attributesFactory, Action<NativeVariantIdentity> action) {
        Collection<Optional<Linkage>> linkages = linkageProvider.getOrElse(Collections.<Linkage>singletonList(null)).stream().map(it -> Optional.ofNullable(it)).collect(Collectors.toList());
        Collection<TargetMachine> targetMachines = targetMachineProvider.get();

        for (BuildType buildType : buildTypes) {
            for (Optional<Linkage> linkage : linkages) {
                for (TargetMachine targetMachine : targetMachines) {
                    ObjectFactory objectFactory = project.getObjects();
                    Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
                    Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);

                    List<String> variantNameToken = Lists.newArrayList();
                    // FIXME: Always build type name to keep parity with previous Gradle version in tooling API
                    variantNameToken.add(buildType.getName());
                    linkage.ifPresent(it -> variantNameToken.add(createDimensionSuffix(it, linkages)));
                    variantNameToken.add(createDimensionSuffix(targetMachine.getOperatingSystemFamily(), targetMachines.stream().map(TargetMachine::getOperatingSystemFamily).collect(Collectors.toSet())));
                    variantNameToken.add(createDimensionSuffix(targetMachine.getArchitecture(), targetMachines.stream().map(TargetMachine::getArchitecture).collect(Collectors.toSet())));

                    String variantName = StringUtils.uncapitalize(String.join("", variantNameToken));

                    Provider<String> group = project.provider(() -> project.getGroup().toString());
                    Provider<String> version = project.provider(() -> project.getVersion().toString());

                    AttributeContainer runtimeAttributes = attributesFactory.mutable();
                    runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                    runtimeAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                    runtimeAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                    runtimeAttributes.attribute(ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());
                    runtimeAttributes.attribute(OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                    linkage.ifPresent(it -> runtimeAttributes.attribute(LINKAGE_ATTRIBUTE, it));

                    DefaultUsageContext runtimeUsageContext = new DefaultUsageContext(variantName + "Runtime", runtimeUsage, runtimeAttributes);

                    DefaultUsageContext linkUsageContext = null;
                    if (linkage.isPresent()) {
                        AttributeContainer linkAttributes = attributesFactory.mutable();
                        linkAttributes.attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                        linkAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                        linkAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                        linkAttributes.attribute(ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());
                        linkAttributes.attribute(OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                        linkage.ifPresent(it -> linkAttributes.attribute(LINKAGE_ATTRIBUTE, it));

                        linkUsageContext = new DefaultUsageContext(variantName + "Link", linkUsage, linkAttributes);
                    }

                    NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, baseName, group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine, linkUsageContext, runtimeUsageContext, linkage.orElse(null));

                    action.execute(variantIdentity);
                }
            }
        }
    }

    /**
     * Used by all native plugins to work around the missing default feature on Property
     *
     * See https://github.com/gradle/gradle-native/issues/918
     *
     * @since 5.1
     */
    public static Set<TargetMachine> getDefaultTargetMachines(TargetMachineFactory targetMachineFactory) {
        return Collections.singleton(((DefaultTargetMachineFactory)targetMachineFactory).host());
    }

    public static boolean isBuildable(NativeVariantIdentity identity) {
        return DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(identity.getTargetMachine().getOperatingSystemFamily().getName());
    }
}
