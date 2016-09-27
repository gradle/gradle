/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.test.cppunit.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.cpp.CppSourceSet;
import org.gradle.language.cpp.plugins.CppLangPlugin;
import org.gradle.model.*;
import org.gradle.nativeplatform.test.cppunit.CppUnitTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.cppunit.CppUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cppunit.internal.DefaultCppUnitTestSuiteBinary;
import org.gradle.nativeplatform.test.cppunit.internal.DefaultCppUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cppunit.tasks.GenerateCppUnitLauncher;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.testing.base.TestSuiteContainer;

import java.io.File;

import static org.gradle.nativeplatform.test.internal.NativeTestSuites.createNativeTestSuiteBinaries;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CppUnit.
 */
@Incubating
public class CppUnitPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CppLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        private static final String CPPUNIT_LAUNCHER_SOURCE_SET = "cppunitLauncher";

        @ComponentType
        public void registerCppUnitTestSuiteSpecType(TypeBuilder<CppUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultCppUnitTestSuiteSpec.class);
        }

        @Mutate
        public void configureCppUnitTestSuiteSources(@Each final CppUnitTestSuiteSpec suite, @Path("buildDir") final File buildDir) {
            suite.getSources().create(CPPUNIT_LAUNCHER_SOURCE_SET, CppSourceSet.class, new Action<CppSourceSet>() {
                @Override
                public void execute(CppSourceSet launcherSources) {
                    File baseDir = new File(buildDir, "src/" + suite.getName() + "/cppunitLauncher");
                    launcherSources.getSource().srcDir(new File(baseDir, "cpp"));
                    launcherSources.getExportedHeaders().srcDir(new File(baseDir, "headers"));
                }
            });

            suite.getSources().withType(CppSourceSet.class).named("cpp", new Action<CppSourceSet>() {
                @Override
                public void execute(CppSourceSet cppSourceSet) {
                    cppSourceSet.lib(suite.getSources().get(CPPUNIT_LAUNCHER_SOURCE_SET));
                }
            });
        }

        @Mutate
        public void createCppUnitLauncherTasks(TaskContainer tasks, TestSuiteContainer testSuites) {
            for (final CppUnitTestSuiteSpec suite : testSuites.withType(CppUnitTestSuiteSpec.class).values()) {

                String taskName = suite.getName() + "CppUnitLauncher";
                GenerateCppUnitLauncher skeletonTask = tasks.create(taskName, GenerateCppUnitLauncher.class);

                CppSourceSet launcherSources = findLauncherSources(suite);
                skeletonTask.setSourceDir(launcherSources.getSource().getSrcDirs().iterator().next());
                skeletonTask.setHeaderDir(launcherSources.getExportedHeaders().getSrcDirs().iterator().next());
                launcherSources.builtBy(skeletonTask);
            }
        }

        private CppSourceSet findLauncherSources(CppUnitTestSuiteSpec suite) {
            return suite.getSources().withType(CppSourceSet.class).get(CPPUNIT_LAUNCHER_SOURCE_SET);
        }

        @ComponentType
        public void registerCppUnitTestBinaryType(TypeBuilder<CppUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultCppUnitTestSuiteBinary.class);
        }

        @ComponentBinaries
        public void createCppUnitTestBinaries(ModelMap<CppUnitTestSuiteBinarySpec> binaries,
                                            CppUnitTestSuiteSpec testSuite,
                                            @Path("buildDir") final File buildDir,
                                            final ServiceRegistry serviceRegistry,
                                            final ITaskFactory taskFactory) {
            createNativeTestSuiteBinaries(binaries, testSuite, CppUnitTestSuiteBinarySpec.class, "CppUnitExe", buildDir, serviceRegistry);
        }
    }

}
