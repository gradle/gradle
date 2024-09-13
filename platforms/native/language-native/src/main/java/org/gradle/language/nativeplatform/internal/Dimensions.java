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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.DefaultSoftwareComponentVariant;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
            return StringUtils.capitalize(dimensionValue.toLowerCase(Locale.ROOT));
        }
        return "";
    }

    private static boolean isDimensionVisible(Collection<?> multivalueProperty) {
        return multivalueProperty.size() > 1;
    }

    public static void unitTestVariants(Provider<String> baseName, SetProperty<TargetMachine> declaredTargetMachines, @Nullable SetProperty<TargetMachine> declaredTargetMachinesOfTestedComponent,
                                        ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory,
                                        Provider<String> group, Provider<String> version,
                                        Action<NativeVariantIdentity> action) {
        Collection<TargetMachine> targetMachines = extractAndValidate("target machine", "unit test", declaredTargetMachines);
        if (declaredTargetMachinesOfTestedComponent != null) {
            Collection<TargetMachine> targetMachinesOfTestedComponent = extractAndValidate("target machine", "component under test", declaredTargetMachinesOfTestedComponent);
            validateTargetMachines(targetMachines, targetMachinesOfTestedComponent);
        }
        variants(baseName, Arrays.asList(BuildType.DEBUG), targetMachines, objectFactory, attributesFactory, group, version, action);
    }

    public static void applicationVariants(Provider<String> baseName, SetProperty<TargetMachine> declaredTargetMachines,
                                       ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory,
                                       Provider<String> group, Provider<String> version,
                                       Action<NativeVariantIdentity> action) {
        Collection<BuildType> buildTypes = BuildType.DEFAULT_BUILD_TYPES;
        Collection<TargetMachine> targetMachines = extractAndValidate("target machine", "application", declaredTargetMachines);
        variants(baseName, buildTypes, targetMachines, objectFactory, attributesFactory, group, version, action);
    }

    public static void libraryVariants(Provider<String> baseName, SetProperty<Linkage> declaredLinkages, SetProperty<TargetMachine> declaredTargetMachines,
                                           ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory,
                                           Provider<String> group, Provider<String> version,
                                           Action<NativeVariantIdentity> action) {
        Collection<BuildType> buildTypes = BuildType.DEFAULT_BUILD_TYPES;
        Collection<Linkage> linkages = extractAndValidate("linkage", "library", declaredLinkages);
        Collection<TargetMachine> targetMachines = extractAndValidate("target machine", "library", declaredTargetMachines);
        variants(baseName, buildTypes, linkages, targetMachines, objectFactory, attributesFactory, group, version, action);
    }

    private static <T> Collection<T> extractAndValidate(String propertyName, String componentName, SetProperty<T> declared) {
        declared.finalizeValue();
        Collection<T> value = declared.get();
        assertNonEmpty(propertyName, componentName, value);
        return value;
    }

    private static void assertNonEmpty(String propertyName, String componentName, Collection<?> property) {
        if (property.isEmpty()) {
            throw new IllegalArgumentException(String.format("A %s needs to be specified for the %s.", propertyName, componentName));
        }
    }

    private static void validateTargetMachines(Collection<TargetMachine> testTargetMachines, Collection<TargetMachine> mainTargetMachines) {
        for (TargetMachine machine : testTargetMachines) {
            if (!mainTargetMachines.contains(machine)) {
                throw new IllegalArgumentException("The target machine " + machine.toString() + " was specified for the unit test, but this target machine was not specified on the component under test.");
            }
        }
    }

    private static void variants(Provider<String> baseName, Collection<BuildType> buildTypes, Collection<Linkage> linkages, Collection<TargetMachine> targetMachines,
                                 ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory,
                                 // TODO: These should come from somewhere else, probably
                                 Provider<String> group, Provider<String> version,
                                 Action<NativeVariantIdentity> action) {
        for (BuildType buildType : buildTypes) {
            for (Linkage linkage : linkages) {
                for (TargetMachine targetMachine : targetMachines) {
                    Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
                    Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);

                    List<String> variantNameToken = new ArrayList<>();
                    // FIXME: Always build type name to keep parity with previous Gradle version in tooling API
                    variantNameToken.add(buildType.getName());
                    variantNameToken.add(createDimensionSuffix(linkage, linkages));
                    variantNameToken.add(createDimensionSuffix(targetMachine.getOperatingSystemFamily(), targetMachinesToOperatingSystems(targetMachines)));
                    variantNameToken.add(createDimensionSuffix(targetMachine.getArchitecture(), targetMachinesToArchitectures(targetMachines)));

                    String variantName = StringUtils.uncapitalize(String.join("", variantNameToken));

                    AttributeContainer runtimeAttributes = attributesFactory.mutable();
                    runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                    addCommonAttributes(buildType, targetMachine, runtimeAttributes);
                    runtimeAttributes.attribute(LINKAGE_ATTRIBUTE, linkage);

                    UsageContext runtimeVariant = new DefaultSoftwareComponentVariant(variantName + "Runtime", runtimeAttributes);

                    AttributeContainer linkAttributes = attributesFactory.mutable();
                    linkAttributes.attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                    addCommonAttributes(buildType, targetMachine, linkAttributes);
                    linkAttributes.attribute(LINKAGE_ATTRIBUTE, linkage);
                    UsageContext linkVariant = new DefaultSoftwareComponentVariant(variantName + "Link", linkAttributes);

                    NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, baseName, group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine, linkVariant, runtimeVariant, linkage);

                    action.execute(variantIdentity);
                }
            }
        }
    }

    private static void variants(Provider<String> baseName, Collection<BuildType> buildTypes, Collection<TargetMachine> targetMachines,
                                 ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory,
                                 // TODO: These should come from somewhere else, probably
                                 Provider<String> group, Provider<String> version,
                                 Action<NativeVariantIdentity> action) {
        for (BuildType buildType : buildTypes) {
            for (TargetMachine targetMachine : targetMachines) {
                Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

                List<String> variantNameToken = new ArrayList<>();
                // FIXME: Always build type name to keep parity with previous Gradle version in tooling API
                variantNameToken.add(buildType.getName());
                variantNameToken.add(createDimensionSuffix(targetMachine.getOperatingSystemFamily(), targetMachinesToOperatingSystems(targetMachines)));
                variantNameToken.add(createDimensionSuffix(targetMachine.getArchitecture(), targetMachinesToArchitectures(targetMachines)));

                String variantName = StringUtils.uncapitalize(String.join("", variantNameToken));

                AttributeContainer runtimeAttributes = attributesFactory.mutable();
                runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                addCommonAttributes(buildType, targetMachine, runtimeAttributes);

                UsageContext runtimeVariant = new DefaultSoftwareComponentVariant(variantName + "Runtime", runtimeAttributes);

                UsageContext linkVariant = null;

                NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, baseName, group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine, linkVariant, runtimeVariant, null);

                action.execute(variantIdentity);
            }
        }
    }

    private static Set<OperatingSystemFamily> targetMachinesToOperatingSystems(Collection<TargetMachine> targetMachines) {
        return targetMachines.stream().map(TargetMachine::getOperatingSystemFamily).collect(Collectors.toSet());
    }

    private static Set<MachineArchitecture> targetMachinesToArchitectures(Collection<TargetMachine> targetMachines) {
        return targetMachines.stream().map(TargetMachine::getArchitecture).collect(Collectors.toSet());
    }

    private static void addCommonAttributes(BuildType buildType, TargetMachine targetMachine, AttributeContainer runtimeAttributes) {
        runtimeAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
        runtimeAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
        runtimeAttributes.attribute(ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());
        runtimeAttributes.attribute(OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
    }

    /**
     * Used by all native plugins to work around the missing default feature on Property
     *
     * See https://github.com/gradle/gradle-native/issues/918
     *
     * @since 5.1
     */
    public static Set<TargetMachine> useHostAsDefaultTargetMachine(TargetMachineFactory targetMachineFactory) {
        return Collections.singleton(((DefaultTargetMachineFactory) targetMachineFactory).host());
    }

    public static boolean tryToBuildOnHost(NativeVariantIdentity identity) {
        return DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(identity.getTargetMachine().getOperatingSystemFamily().getName());
    }
}
