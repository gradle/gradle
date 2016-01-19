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
import org.gradle.internal.Cast;
import org.gradle.internal.Transformers;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmComponentSpec;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JvmTestSuiteBinarySpec;
import org.gradle.jvm.test.JvmTestSuiteSpec;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.internal.model.DefaultVariantsMetaData;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.Defaults;
import org.gradle.model.ModelMap;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.internal.*;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class JvmTestSuiteRules extends RuleSource {

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
                configureReports((JvmTestSuiteBinarySpecInternal) binary, test);
            }

            private void configureReports(JvmTestSuiteBinarySpecInternal binary, Test test) {
                // todo: improve configuration of reports
                TestTaskReports reports = test.getReports();
                File reportsDirectory = new File(buildDir, "reports");
                File reportsOutputDirectory = binary.getNamingScheme().getOutputDirectory(reportsDirectory);
                File htmlDir = new File(reportsOutputDirectory, "tests");
                File xmlDir = new File(buildDir, "test-results");
                File xmlDirOutputDirectory = binary.getNamingScheme().getOutputDirectory(xmlDir);
                File binDir = new File(xmlDirOutputDirectory, "binary");
                reports.getHtml().setDestination(htmlDir);
                reports.getJunitXml().setDestination(xmlDirOutputDirectory);
                test.setBinResultsDir(binDir);
            }
        });
    }

    @Defaults
    public void createTestSuiteTasks(
        final ModelMap<JvmTestSuiteBinarySpec> binaries,
        final @Path("buildDir") File buildDir) {
        binaries.afterEach(new Action<JvmTestSuiteBinarySpec>() {
            @Override
            public void execute(JvmTestSuiteBinarySpec binary) {
                final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();
                createJvmTestSuiteTasks(binary, jvmAssembly, buildDir);
            }
        });
    }

    /**
     * Create binaries for test suites.
     * TODO: This should really be a @ComponentBinaries rule, but at this point we have no clue what the concrete binary type is,
     * so everything has to be duplicated in specific plugins. See usages for example.
     */
    public static void createJvmTestSuiteBinaries(ModelMap<BinarySpec> testBinaries,
                                                  ServiceRegistry registry,
                                                  PlatformResolvers platformResolver,
                                                  JvmTestSuiteSpec testSuite,
                                                  JavaToolChainRegistry toolChains,
                                                  ModelSchemaStore modelSchemaStore,
                                                  Class<? extends JvmTestSuiteBinarySpec> testSuiteBinary) {
        JvmComponentSpec testedComponent = testSuite.getTestedComponent();
        if (testedComponent == null) {
            // standalone test suite
            createJvmTestSuiteBinary(testBinaries, testSuiteBinary, testSuite, null, toolChains, platformResolver, registry, modelSchemaStore);
        } else {
            // component under test
            for (final JvmBinarySpec testedBinary : testedBinariesOf(testSuite)) {
                createJvmTestSuiteBinary(testBinaries, testSuiteBinary, testSuite, testedBinary, toolChains, platformResolver, registry, modelSchemaStore);
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
                                                                                    final ModelSchemaStore modelSchemaStore) {

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
                injectDependencyResolutionServices(testBinary);
                configureCompileClasspath(testBinary);
            }

            private boolean addTestSuiteDependencies(JvmTestSuiteBinarySpecInternal testBinary) {
                return testBinary.getDependencies().addAll(testSuite.getDependencies().getDependencies());
            }

            private void injectDependencyResolutionServices(JvmTestSuiteBinarySpecInternal testBinary) {
                ArtifactDependencyResolver dependencyResolver = serviceRegistry.get(ArtifactDependencyResolver.class);
                RepositoryHandler repositories = serviceRegistry.get(RepositoryHandler.class);
                List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
                testBinary.setArtifactDependencyResolver(dependencyResolver);
                testBinary.setRepositories(resolutionAwareRepositories);
                ModelSchema<? extends JvmTestSuiteBinarySpec> schema = Cast.uncheckedCast(modelSchemaStore.getSchema(((BinarySpecInternal) testBinary).getPublicType()));
                testBinary.setVariantsMetaData(DefaultVariantsMetaData.extractFrom(testBinary, schema));
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

    public static Collection<JvmBinarySpec> testedBinariesOf(JvmTestSuiteSpec testSuite) {
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
