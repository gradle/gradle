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

import org.apache.commons.lang.WordUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.gradle.internal.Transformers;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.jvm.test.JvmTestSuiteSpec;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.*;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Functions for creation and configuration of JVM test suites.
 */
public class JvmTestSuites {

    public static <T extends JvmTestSuiteBinarySpec> void createJvmTestSuiteBinaries(
        ModelMap<BinarySpec> testBinaries,
        ServiceRegistry registry,
        JvmTestSuiteSpec testSuite,
        Class<T> testSuiteBinaryClass,
        JavaToolChainRegistry toolChains,
        PlatformResolvers platformResolver,
        ModelSchemaStore modelSchemaStore,
        Action<? super T> configureAction) {
        JvmComponentSpec testedComponent = testSuite.getTestedComponent();
        if (testedComponent == null) {
            // standalone test suite
            createJvmTestSuiteBinary(testBinaries, testSuiteBinaryClass, testSuite, null, toolChains, platformResolver, registry, modelSchemaStore, configureAction);
        } else {
            // component under test
            for (final JvmBinarySpec testedBinary : testedBinariesOf(registry, testSuite)) {
                createJvmTestSuiteBinary(testBinaries, testSuiteBinaryClass, testSuite, testedBinary, toolChains, platformResolver, registry, modelSchemaStore, configureAction);
            }
        }
    }

    private static <T extends JvmTestSuiteBinarySpec> void createJvmTestSuiteBinary(final ModelMap<BinarySpec> testBinaries,
                                                                                    Class<T> testSuiteBinaryClass,
                                                                                    final JvmTestSuiteSpec testSuite,
                                                                                    final JvmBinarySpec testedBinary,
                                                                                    final JavaToolChainRegistry toolChains,
                                                                                    PlatformResolvers platformResolver,
                                                                                    final ServiceRegistry serviceRegistry,
                                                                                    final ModelSchemaStore modelSchemaStore,
                                                                                    final Action<? super T> configureAction) {

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
                injectDependencyResolutionServices(testBinary);
                configureAction.execute(binary);
                configureCompileClasspath(testBinary);
            }

            private void injectDependencyResolutionServices(JvmTestSuiteBinarySpecInternal testBinary) {
                ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
                RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
                List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
                testBinary.setArtifactDependencyResolver(dependencyResolver);
                testBinary.setRepositories(resolutionAwareRepositories);
                testBinary.setModelSchemaStore(modelSchemaStore);
            }

            private void configureCompileClasspath(JvmTestSuiteBinarySpecInternal testSuiteBinary) {
                Collection<DependencySpec> dependencies = testSuiteBinary.getDependencies();
                if (testedBinary != null) {
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

    public static void createJvmTestSuiteTasks(final JvmTestSuiteBinarySpec binary,
                                               final JvmAssembly jvmAssembly,
                                               final File buildDir) {
        binary.getTasks().create(testTaskNameFor(binary), Test.class, new Action<Test>() {
            @Override
            public void execute(final Test test) {
                test.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                test.setDescription(String.format("Runs %s.", WordUtils.uncapitalize(binary.getDisplayName())));
                test.dependsOn(jvmAssembly);
                test.setTestClassesDir(binary.getClassesDir());
                test.setClasspath(binary.getRuntimeClasspath());
                configureReports(test);
            }

            private void configureReports(Test test) {
                // todo: improve configuration of reports
                TestTaskReports reports = test.getReports();
                File reportsDirectory = new File(buildDir, "reports");
                File htmlDir = new File(reportsDirectory, "tests");
                File xmlDir = new File(buildDir, "test-results");
                File binDir = new File(xmlDir, "binary");
                reports.getHtml().setDestination(htmlDir);
                reports.getJunitXml().setDestination(xmlDir);
                test.setBinResultsDir(binDir);
            }
        });
    }

    public static Collection<JvmBinarySpec> testedBinariesOf(ServiceRegistry registry, JvmTestSuiteSpec testSuite) {
        return testedBinariesWithType(JvmBinarySpec.class, testSuite);
    }

    public static <S> Collection<S> testedBinariesWithType(Class<S> type, JvmTestSuiteSpec testSuite) {
        JvmComponentSpec spec = testSuite.getTestedComponent();
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

    private static String testTaskNameFor(JvmTestSuiteBinarySpec binary) {
        return ((BinarySpecInternal) binary).getProjectScopedName() + "Test";
    }

}
