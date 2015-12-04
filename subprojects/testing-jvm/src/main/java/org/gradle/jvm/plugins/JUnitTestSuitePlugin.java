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
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
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
import org.gradle.model.*;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolvers;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
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
                                 final @Path("buildDir") File buildDir) {
            String taskName = binary.getName() + "Test";
            tasks.create(taskName, Test.class, new Action<Test>() {
                @Override
                public void execute(final Test test) {
                    test.setTestClassesDir(binary.getClassesDir());

                    configureReports(test);
                    configureTaskDependencies(test);
                }

                private void configureTaskDependencies(final Test test) {
                    binary.getTasks().withType(PlatformJavaCompile.class).all(new Action<PlatformJavaCompile>() {
                        @Override
                        public void execute(PlatformJavaCompile platformJavaCompile) {
                            // todo: we should probably find a way to better register dependencies
                            // for the test task
                            test.dependsOn(platformJavaCompile);
                        }
                    });
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
        void configureClasspath(final ModelMap<Test> testTasks, final FileOperations fileOperations) {
            testTasks.all(new Action<Test>() {
                @Override
                public void execute(Test test) {
                    Set<? extends Task> tasks = test.getTaskDependencies().getDependencies(test);
                    List<Object> classpath = new LinkedList<Object>();
                    for (Task task : tasks) {
                        if (task instanceof PlatformJavaCompile) {
                            FileCollection cp = ((PlatformJavaCompile) task).getClasspath();
                            if (cp!=null) {
                                classpath.add(cp);
                            }
                        }
                    }
                    classpath.add(test.getTestClassesDir());
                    test.setClasspath(fileOperations.files(classpath.toArray(new Object[classpath.size()])));
                }
            });
        }

        @ComponentBinaries
        void createJUnitBinaries(final ModelMap<JUnitTestSuiteBinarySpec> testBinaries, final PlatformResolvers platformResolver, final JUnitTestSuiteSpec testSuite) {
            testBinaries.create(testSuite.getName(), JUnitTestSuiteBinarySpec.class, new Action<JUnitTestSuiteBinarySpec>() {
                @Override
                public void execute(JUnitTestSuiteBinarySpec jUnitTestSuiteBinarySpec) {
                    final String jUnitVersion = testSuite.getJUnitVersion();
                    jUnitTestSuiteBinarySpec.setJUnitVersion(jUnitVersion);
                    jUnitTestSuiteBinarySpec.setTargetPlatform(resolvePlatforms(platformResolver).get(0));
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
        void addDefaultJavaSourceSet(ModelMap<JUnitTestSuiteSpec> suites) {
            suites.all(new Action<JUnitTestSuiteSpec>() {
                @Override
                public void execute(JUnitTestSuiteSpec jUnitTestSuiteSpec) {
                    jUnitTestSuiteSpec.getSources().create("java", JavaSourceSet.class, new Action<JavaSourceSet>() {
                        @Override
                        public void execute(JavaSourceSet languageSourceSet) {
                            languageSourceSet.getSource().srcDir("src/test/java");
                        }
                    });
                }
            });
        }

        private static List<JavaPlatform> resolvePlatforms(final PlatformResolvers platformResolver) {
            PlatformRequirement defaultPlatformRequirement = DefaultPlatformRequirement.create(DefaultJavaPlatform.current().getName());
            return Collections.singletonList(platformResolver.resolve(JavaPlatform.class, defaultPlatformRequirement));
        }

    }
}
