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
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.test.JUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.JUnitTestSuiteSpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteBinarySpec;
import org.gradle.jvm.test.internal.DefaultJUnitTestSuiteSpec;
import org.gradle.language.base.DependentSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.model.*;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.*;

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
    public void apply(Project target) {
    }

    @SuppressWarnings("unused")
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
            String taskName = ((BinarySpecInternal)binary).getProjectScopedName() + "Test";
            final JvmAssembly jvmAssembly = ((WithJvmAssembly) binary).getAssembly();

            tasks.create(taskName, Test.class, new Action<Test>() {
                @Override
                public void execute(final Test test) {
                    test.dependsOn(jvmAssembly);

                    test.setTestClassesDir(binary.getClassesDir());

                    FileCollection jvmAssemblyOutput = fileOperations.files(jvmAssembly.getClassDirectories(), jvmAssembly.getResourceDirectories());
                    test.setClasspath(jvmAssemblyOutput);

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

                    test.setClasspath(testClasspath);
                }
            });
        }

        @ComponentBinaries
        void createJUnitBinaries(final ModelMap<JUnitTestSuiteBinarySpec> testBinaries, final PlatformResolvers platformResolver, final JUnitTestSuiteSpec testSuite) {
            final List<JavaPlatform> javaPlatforms = resolvePlatforms(platformResolver);
            final JavaPlatform platform = javaPlatforms.get(0);
            final BinaryNamingScheme namingScheme = namingSchemeFor(testSuite, javaPlatforms, platform);
            testBinaries.create(namingScheme.getBinaryName(), JUnitTestSuiteBinarySpec.class, new Action<JUnitTestSuiteBinarySpec>() {
                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    final String jUnitVersion = testSuite.getJUnitVersion();
                    ((BinarySpecInternal)jUnitTestSuiteBinarySpec).setNamingScheme(namingScheme);
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    jUnitTestSuiteBinarySpec.setTargetPlatform(platform);
                    testSuite.getSources().all(new Action<LanguageSourceSet>() {
                        @Override
                        public void execute(LanguageSourceSet languageSourceSet) {
                            // For now, dependencies have to be defined at the source set level
                            // in order for the dependency resolution engine to kick in
                            if (languageSourceSet instanceof DependentSourceSet) {
                                ((DependentSourceSet) languageSourceSet).getDependencies().group("junit").module("junit").version(jUnitVersion);
                            }
                        }
                    });
                }
            });
        }

        @Defaults
        void addDefaultSourceSets(ModelMap<JUnitTestSuiteSpec> suites) {
            suites.all(new Action<JUnitTestSuiteSpec>() {
                @Override
                public void execute(final JUnitTestSuiteSpec jUnitTestSuiteSpec) {
                    jUnitTestSuiteSpec.getSources().create("java", JavaSourceSet.class, new Action<JavaSourceSet>() {
                        @Override
                        public void execute(JavaSourceSet languageSourceSet) {
                            languageSourceSet.getSource().srcDir(String.format("src/%s/java", jUnitTestSuiteSpec.getName()));
                        }
                    });
                    jUnitTestSuiteSpec.getSources().create("resources", JvmResourceSet.class, new Action<JvmResourceSet>() {
                        @Override
                        public void execute(JvmResourceSet languageSourceSet) {
                            languageSourceSet.getSource().srcDir(String.format("src/%s/resources", jUnitTestSuiteSpec.getName()));
                        }
                    });
                }
            });
        }

        @Validate
        void validateJUnitTestSuites(ModelMap<JUnitTestSuiteSpec> suites) {
            suites.all(new Action<JUnitTestSuiteSpec>() {
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

    }
}
