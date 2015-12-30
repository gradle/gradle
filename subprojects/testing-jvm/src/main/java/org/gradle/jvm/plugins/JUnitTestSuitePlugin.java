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
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.model.*;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;
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

            final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();
            tasks.create(testTaskNameFor(binary), Test.class, new Action<Test>() {
                @Override
                public void execute(final Test test) {
                    test.dependsOn(jvmAssembly);
                    test.setTestClassesDir(binary.getClassesDir());
                    test.setClasspath(classpathFor(jvmAssembly, fileOperations));
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
         * Create binaries for test suites.
         */
        @ComponentBinaries
        void createJUnitComponentBinaries(ModelMap<BinarySpec> testBinaries, PlatformResolvers platformResolver, final JUnitTestSuiteSpec testSuite, final JavaToolChainRegistry toolChains) {
            final List<JavaPlatform> javaPlatforms = resolvePlatforms(platformResolver);
            final JavaPlatform platform = javaPlatforms.get(0);
            final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, javaPlatforms, platform);
            testBinaries.create(namingScheme.getBinaryName(), JUnitTestSuiteBinarySpec.class, new Action<JUnitTestSuiteBinarySpec>() {

                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    final String jUnitVersion = testSuite.getJUnitVersion();
                    ((BinarySpecInternal) jUnitTestSuiteBinarySpec).setNamingScheme(namingScheme);
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    jUnitTestSuiteBinarySpec.setTargetPlatform(platform);
                    jUnitTestSuiteBinarySpec.setToolChain(toolChains.getForPlatform(platform));

                    DependencySpecContainer dependencies = testSuite.getDependencies();
                    addJUnitDependencyTo(dependencies, jUnitVersion);
                    setDependenciesOf(jUnitTestSuiteBinarySpec, dependencies);
                }
            });
        }

        private void setDependenciesOf(JUnitTestSuiteBinarySpec binary, DependencySpecContainer dependencies) {
            ((WithDependencies) binary).setDependencies(dependencies.getDependencies());
        }

        private void addJUnitDependencyTo(DependencySpecContainer dependencies, String jUnitVersion) {
            dependencies.group("junit").module("junit").version(jUnitVersion);
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

    private static String testTaskNameFor(JUnitTestSuiteBinarySpec binary) {
        return ((BinarySpecInternal) binary).getProjectScopedName() + "Test";
    }
}
