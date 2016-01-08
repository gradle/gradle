/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.jvm.test.internal;

import org.gradle.api.Action;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.jvm.test.JvmTestSuiteSpec;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.internal.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Functions for creation and configuration of native test suites.
 */
public class JvmTestSuites {

    private static final ModelType<JvmComponentSpec> JVM_COMPONENT_SPEC_MODEL_TYPE = ModelType.of(JvmComponentSpec.class);

    public static <T extends JvmTestSuiteBinarySpec> void createJvmTestSuiteBinaries(
        ModelMap<BinarySpec> testBinaries,
        ServiceRegistry registry,
        JvmTestSuiteSpec testSuite,
        Class<T> testSuiteBinaryClass,
        JavaToolChainRegistry toolChains,
        PlatformResolvers platformResolver,
        Action<? super T> configureAction) {
        String testedComponent = testSuite.getTestedComponent();
        if (testedComponent == null) {
            // standalone test suite
            createJvmTestSuiteBinary(testBinaries, testSuiteBinaryClass, testSuite, null, toolChains, platformResolver, configureAction);
        } else {
            // component under test
            for (final JvmBinarySpec testedBinary : testedBinariesOf(registry, testSuite)) {
                createJvmTestSuiteBinary(testBinaries, testSuiteBinaryClass, testSuite, testedBinary, toolChains, platformResolver, configureAction);
            }
        }
    }

    private static <T extends JvmTestSuiteBinarySpec> void createJvmTestSuiteBinary(ModelMap<BinarySpec> testBinaries,
                                                                                    Class<T> testSuiteBinaryClass,
                                                                                    JvmTestSuiteSpec testSuite,
                                                                                    final JvmBinarySpec testedBinary,
                                                                                    final JavaToolChainRegistry toolChains,
                                                                                    PlatformResolvers platformResolver,
                                                                                    final Action<? super T> configureAction) {

        final List<JavaPlatform> javaPlatforms = resolvePlatforms(platformResolver);
        final JavaPlatform platform = javaPlatforms.get(0);
        final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, testedBinary, javaPlatforms, platform);

        testBinaries.create(namingScheme.getBinaryName(), testSuiteBinaryClass, new Action<T>() {
            @Override
            public void execute(T binary) {
                JvmTestSuiteBinarySpecInternal testBinary = (JvmTestSuiteBinarySpecInternal) binary;
                testBinary.setNamingScheme(namingScheme);
                testBinary.setTargetPlatform(platform);
                testBinary.setToolChain(toolChains.getForPlatform(platform));
                testBinary.setTestedBinary(testedBinary);
                configureAction.execute(binary);
            }
        });
    }

    public static Collection<JvmBinarySpec> testedBinariesOf(ServiceRegistry registry, JvmTestSuiteSpec testSuite) {
        return testedBinariesWithType(registry, JvmBinarySpec.class, testSuite);
    }

    public static <S> Collection<S> testedBinariesWithType(ServiceRegistry registry, Class<S> type, JvmTestSuiteSpec testSuite) {
        String testedComponent = testSuite.getTestedComponent();
        JvmComponentSpec spec = getTestedComponent(registry, testedComponent);
        if (spec == null) {
            throw new InvalidModelException(String.format("Component '%s' declared under test '%s' does not exist", testedComponent, testSuite.getDisplayName()));
        }
        return spec.getBinaries().withType(type).values();
    }

    public static JvmComponentSpec getTestedComponent(ServiceRegistry registry, String testedComponent) {
        ModelRegistry model = registry.get(ModelRegistry.class);
        ModelPath path = ModelPath.path(Arrays.asList("components", testedComponent));
        JvmComponentSpec jvmComponentSpec = model.find(path, JVM_COMPONENT_SPEC_MODEL_TYPE);
        if (jvmComponentSpec ==null) {
            return null;
        }
        return jvmComponentSpec;
    }

    private static BinaryNamingScheme namingSchemeFor(JvmTestSuiteSpec testSuiteSpec, JvmBinarySpec testedBinary, List<JavaPlatform> selectedPlatforms, JavaPlatform platform) {
        BinaryNamingScheme namingScheme = DefaultBinaryNamingScheme.component(testSuiteSpec.getName())
            .withBinaryType("binary") // not a 'Jar', not a 'test'
            .withRole("assembly", true)
            .withVariantDimension(platform, selectedPlatforms);
        if (testedBinary != null) {
            return namingScheme.withVariantDimension(((BinarySpecInternal)testedBinary).getProjectScopedName());
        }
        return namingScheme;
    }

    private static List<JavaPlatform> resolvePlatforms(final PlatformResolvers platformResolver) {
        PlatformRequirement defaultPlatformRequirement = DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName());
        return Collections.singletonList(platformResolver.resolve(JavaPlatform.class, defaultPlatformRequirement));
    }


}
