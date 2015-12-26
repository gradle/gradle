/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.jvm.plugins;

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithDependencies;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteSpec;
import org.gradle.jvm.toolchain.JavaToolChainRegistry;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.model.*;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
import org.gradle.platform.base.test.TestSuiteContainer;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This plugin adds support for execution of JUnit test suites to the Java software model.
 *
 * @since 2.11
 */
@Incubating
public class JUnitTestSuitePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(TestingModelBasePlugin.class);
        project.getPluginManager().apply(JvmComponentPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @ComponentType
        public void register(ComponentTypeBuilder<JUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteSpec.class);
        }

        @BinaryType
        public void registerJUnitBinary(BinaryTypeBuilder<JUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultJUnitTestSuiteBinarySpec.class);
        }

        @BinaryTasks
        void createTestSuiteTask(final ModelMap<Task> tasks,
                                 final JUnitTestSuiteBinarySpec binary,
                                 final FileOperations fileOperations,
                                 final @Path("buildDir") File buildDir) {
            String taskName = ((BinarySpecInternal) binary).getProjectScopedName() + "Test";
            final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();

            tasks.create(taskName, Test.class, new Action<Test>() {
                @Override
                public void execute(final Test test) {
                    test.dependsOn(jvmAssembly);

                    test.setTestClassesDir(binary.getClassesDir());

                    // TODO:RBO is this even necessary? what about the `configureClasspath` rule below?
                    test.setClasspath(classpathFor(jvmAssembly, fileOperations));

                    configureReports(test);
                }

                private File configureReports(Test test) {
                    // todo: improve configuration of reports
                    TestTaskReports reports = test.getReports();
                    File reportsDirectory = new File(buildDir, "reports");
                    File htmlDir = new File(reportsDirectory, "tests");
                    File xmlDir = new File(buildDir, "test-results");
                    File binDir = new File(xmlDir, "binary");
                    reports.getHtml().setDestination(htmlDir);
                    reports.getJunitXml().setDestination(xmlDir);
                    test.setBinResultsDir(binDir);
                    return reportsDirectory;
                }
            });
        }

        @Mutate
        void configureClasspath(final ModelMap<Test> testTasks) {
            testTasks.all(new Action<Test>() {
                @Override
                public void execute(Test test) {
                    test.setClasspath(classpathFor(test));
                }
            });
        }

        /**
         * Create binaries for test suites declared under {@code testSuites}.
         *
         * These are not built as part of {@code assemble}.
         */
        @Mutate // TODO:RBO this is only necessary until we relax @ComponentBinaries to allow components anywhere (particularly under testSuites)
        void createJUnitTestSuiteBinaries(TestSuiteContainer testSuites, final PlatformResolvers platformResolvers, final JavaToolChainRegistry toolChains) {
            testSuites.afterEach(JUnitTestSuiteSpec.class, new Action<JUnitTestSuiteSpec>() {
                @Override
                public void execute(JUnitTestSuiteSpec jUnitTestSuiteSpec) {
                    createJUnitBinariesFor(jUnitTestSuiteSpec, false, platformResolvers, jUnitTestSuiteSpec.getBinaries(), toolChains);
                }
            });
        }

        /**
         * Create binaries for test suites declared under {@code components}.
         *
         * These are built as part of {@code assemble}.
         */
        @ComponentBinaries
        void createJUnitComponentBinaries(ModelMap<BinarySpec> testBinaries, PlatformResolvers platformResolver, JUnitTestSuiteSpec testSuite, JavaToolChainRegistry toolChains) {
            createJUnitBinariesFor(testSuite, true, platformResolver, testBinaries, toolChains);
        }

        private void createJUnitBinariesFor(final JUnitTestSuiteSpec testSuite, final boolean buildable, PlatformResolvers platformResolver, ModelMap<BinarySpec> testBinaries, final JavaToolChainRegistry toolChains) {
            final List<JavaPlatform> javaPlatforms = resolvePlatforms(platformResolver);
            final JavaPlatform platform = javaPlatforms.get(0);
            final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, javaPlatforms, platform);
            testBinaries.create(namingScheme.getBinaryName(), JUnitTestSuiteBinarySpec.class, new Action<JUnitTestSuiteBinarySpec>() {

                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    //TODO:RBO this is just temporary to avoid merge hell later, a better solution is in the works
                    ((BinarySpecInternal) jUnitTestSuiteBinarySpec).setBuildable(buildable);

                    final String jUnitVersion = testSuite.getJUnitVersion();
                    ((BinarySpecInternal) jUnitTestSuiteBinarySpec).setNamingScheme(namingScheme);
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    jUnitTestSuiteBinarySpec.setTargetPlatform(platform);
                    jUnitTestSuiteBinarySpec.setToolChain(toolChains.getForPlatform(platform));
                    ((WithDependencies) jUnitTestSuiteBinarySpec).setDependencies(testSuite.getDependencies().getDependencies());
                    testSuite.getSources().all(new Action<LanguageSourceSet>() {
                        @Override
                        public void execute(LanguageSourceSet languageSourceSet) {
                            // For now, dependencies have to be defined at the source set level
                            // in order for the dependency resolution engine to kick in
                            if (languageSourceSet instanceof DependentSourceSet) {
                                // TODO:RBO consider adding this to the testSuite dependencies container
                                ((DependentSourceSet) languageSourceSet).getDependencies().group("junit").module("junit").version(jUnitVersion);
                            }
                        }
                    });
                }
            });
        }

        /**
         * Add default source sets to test suites declared under {@code components}.
         */
        @Defaults
        void addDefaultSourceSetsToTestSuiteComponents(@Path("components") ModelMap<JUnitTestSuiteSpec> components) {
            addDefaultSourceSetsTo(components);
        }

        /**
         * Add default source sets to test suites declared under {@code testSuites}.
         */
        @Defaults
        void addDefaultSourceSetsToTestSuites(@Path("testSuites") ModelMap<JUnitTestSuiteSpec> testSuites) {
            addDefaultSourceSetsTo(testSuites);
        }

        private void addDefaultSourceSetsTo(ModelMap<JUnitTestSuiteSpec> suites) {
            suites.all(new Action<JUnitTestSuiteSpec>() {
                @Override
                public void execute(final JUnitTestSuiteSpec jUnitTestSuiteSpec) {
                    addSourceSetTo(jUnitTestSuiteSpec, "java", JavaSourceSet.class);
                    addSourceSetTo(jUnitTestSuiteSpec, "resources", JvmResourceSet.class);
                }
            });
        }

        private void addSourceSetTo(final JUnitTestSuiteSpec spec, final String sourceSetName, Class<? extends LanguageSourceSet> type) {
            spec.getSources().create(sourceSetName, type, new Action<LanguageSourceSet>() {
                @Override
                public void execute(LanguageSourceSet languageSourceSet) {
                    languageSourceSet.getSource().srcDir(String.format("src/%s/%s", spec.getName(), sourceSetName));
                }
            });
        }

        /**
         * Validate test suites declared under {@code components}.
         */
        @Validate
        void validateJUnitTestSuiteComponents(@Path("components") ModelMap<JUnitTestSuiteSpec> components) {
           validateJUnitTestSuitesIn(components);
        }

        /**
         * Validate test suites declared under {@code testSuites}.
         */
        @Validate
        void validateJUnitTestSuites(@Path("testSuites") ModelMap<JUnitTestSuiteSpec> testSuites) {
            validateJUnitTestSuitesIn(testSuites);
        }

        private void validateJUnitTestSuitesIn(ModelMap<JUnitTestSuiteSpec> testSuites) {
            testSuites.all(new Action<JUnitTestSuiteSpec>() {
                @Override
                public void execute(JUnitTestSuiteSpec jUnitTestSuiteSpec) {
                    if (jUnitTestSuiteSpec.getJUnitVersion() == null) {
                        throw new InvalidModelException(
                            String.format("Test suite '%s' doesn't declare JUnit version. Please specify it with `jUnitVersion '4.12'` for example.", jUnitTestSuiteSpec.getName()));
                    }
                }
            });
        }

        private static List<JavaPlatform> resolvePlatforms(final PlatformResolvers platformResolver) {
            PlatformRequirement defaultPlatformRequirement = DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName());
            return Collections.singletonList(platformResolver.resolve(JavaPlatform.class, defaultPlatformRequirement));
        }

        private BinaryNamingScheme namingSchemeFor(JUnitTestSuiteSpec testSuiteSpec, List<JavaPlatform> selectedPlatforms, JavaPlatform platform) {
            return DefaultBinaryNamingScheme.component(testSuiteSpec.getName())
                .withBinaryType("binary") // not a 'Jar', not a 'test'
                .withRole("assembly", true)
                .withVariantDimension(platform, selectedPlatforms);
        }

        private FileCollection classpathFor(JvmAssembly jvmAssembly, FileOperations fileOperations) {
            return fileOperations.files(jvmAssembly.getClassDirectories(), jvmAssembly.getResourceDirectories());
        }

        private FileCollection classpathFor(Test test) {
            UnionFileCollection testClasspath = new UnionFileCollection(test.getClasspath());
            Set<? extends Task> tasks = test.getTaskDependencies().getDependencies(test);
            for (Task task : tasks) {
                if (task instanceof PlatformJavaCompile) {
                    FileCollection cp = ((PlatformJavaCompile) task).getClasspath();
                    if (cp != null) {
                        testClasspath.add(cp);
                    }
                }
            }
            return testClasspath;
        }
    }
}
