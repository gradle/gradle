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
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.jvm.test.JvmTestSuiteSpec;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.model.ModelMap;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.VariantComponentSpec;
import org.gradle.platform.base.internal.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JvmTestSuiteRules {

    /**
     * Create binaries for test suites. TODO: This should really be a @ComponentBinaries rule, but at this point we have no clue what the concrete binary type is, so everything has to be duplicated in
     * specific plugins. See usages for example.
     */
    public static void createJvmTestSuiteBinaries(ModelMap<BinarySpec> testBinaries,
                                                  PlatformResolvers platformResolver,
                                                  JvmTestSuiteSpec testSuite,
                                                  JavaToolChainRegistry toolChains,
                                                  Class<? extends JvmTestSuiteBinarySpec> testSuiteBinary) {
        JvmComponentSpec testedComponent = testSuite.getTestedComponent();
        if (testedComponent == null) {
            // standalone test suite
            createJvmTestSuiteBinary(testBinaries, testSuiteBinary, testSuite, null, toolChains, platformResolver);
        } else {
            // component under test
            for (final JvmBinarySpec testedBinary : testedBinariesOf(testSuite)) {
                createJvmTestSuiteBinary(testBinaries, testSuiteBinary, testSuite, testedBinary, toolChains, platformResolver);
            }
        }
    }

    private static <T extends JvmTestSuiteBinarySpec> void createJvmTestSuiteBinary(final ModelMap<BinarySpec> testBinaries,
                                                                                    Class<T> testSuiteBinaryClass,
                                                                                    final JvmTestSuiteSpec testSuite,
                                                                                    final JvmBinarySpec testedBinary,
                                                                                    final JavaToolChainRegistry toolChains,
                                                                                    PlatformResolvers platformResolver) {

        final List<JavaPlatform> javaPlatforms = resolvePlatforms(platformResolver);
        final JavaPlatform platform = testedBinary != null ? testedBinary.getTargetPlatform() : javaPlatforms.get(0);
        final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, testedBinary, javaPlatforms, platform);

        testBinaries.create(namingScheme.getBinaryName(), testSuiteBinaryClass, new Action<T>() {
            @Override
            public void execute(T binary) {
                JvmTestSuiteBinarySpecInternal testBinary = (JvmTestSuiteBinarySpecInternal) binary;
                testBinary.setNamingScheme(namingScheme);
                testBinary.setTargetPlatform(platform);
                testBinary.setToolChain(toolChains.getForPlatform(platform));
                testBinary.setTestedBinary(testedBinary);
                addTestSuiteDependencies(testBinary);
                configureCompileClasspath(testBinary);
            }

            private boolean addTestSuiteDependencies(JvmTestSuiteBinarySpecInternal testBinary) {
                return testBinary.getDependencies().addAll(testSuite.getDependencies().getDependencies());
            }


            private void configureCompileClasspath(JvmTestSuiteBinarySpecInternal testSuiteBinary) {
                if (testedBinary != null) {
                    Collection<DependencySpec> dependencies = testSuiteBinary.getDependencies();
                    BinarySpecInternal binary = (BinarySpecInternal) testedBinary;
                    LibraryBinaryIdentifier id = binary.getId();
                    dependencies.add(DefaultLibraryBinaryDependencySpec.of(id));
                    if (testedBinary instanceof JarBinarySpecInternal) {
                        dependencies.addAll(((JarBinarySpecInternal) testedBinary).getApiDependencies());
                    }
                }
            }

        });
    }

    private static Collection<JvmBinarySpec> testedBinariesOf(JvmTestSuiteSpec testSuite) {
        return testedBinariesWithType(JvmBinarySpec.class, testSuite);
    }

    private static <S> Collection<S> testedBinariesWithType(Class<S> type, JvmTestSuiteSpec testSuite) {
        VariantComponentSpec spec = (VariantComponentSpec) testSuite.getTestedComponent();
        return spec.getBinaries().withType(type).values();
    }

    private static BinaryNamingScheme namingSchemeFor(JvmTestSuiteSpec testSuiteSpec, JvmBinarySpec testedBinary, List<JavaPlatform> selectedPlatforms, JavaPlatform platform) {
        BinaryNamingScheme namingScheme = DefaultBinaryNamingScheme.component(testSuiteSpec.getName())
            .withBinaryType("binary") // not a 'Jar', not a 'test'
            .withRole("assembly", true)
            .withVariantDimension(platform, selectedPlatforms);
        if (testedBinary != null) {
            return namingScheme.withVariantDimension(((BinarySpecInternal) testedBinary).getProjectScopedName());
        }
        return namingScheme;
    }

    private static List<JavaPlatform> resolvePlatforms(final PlatformResolvers platformResolver) {
        PlatformRequirement defaultPlatformRequirement = DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName());
        return Collections.singletonList(platformResolver.resolve(JavaPlatform.class, defaultPlatformRequirement));
    }
}
