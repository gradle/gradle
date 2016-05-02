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

package org.gradle.nativeplatform.test.cunit.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.plugins.CLangPlugin;
import org.gradle.model.*;
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteBinary;
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cunit.tasks.GenerateCUnitLauncher;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.testing.base.TestSuiteContainer;

import java.io.File;

import static org.gradle.nativeplatform.test.internal.NativeTestSuites.createNativeTestSuiteBinaries;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class CUnitPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        private static final String CUNIT_LAUNCHER_SOURCE_SET = "cunitLauncher";

        @ComponentType
        public void registerCUnitTestSuiteSpecType(TypeBuilder<CUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultCUnitTestSuiteSpec.class);
        }

        @Mutate
        public void configureCUnitTestSuiteSources(@Each final CUnitTestSuiteSpec suite, @Path("buildDir") final File buildDir) {
            suite.getSources().create(CUNIT_LAUNCHER_SOURCE_SET, CSourceSet.class, new Action<CSourceSet>() {
                @Override
                public void execute(CSourceSet launcherSources) {
                    File baseDir = new File(buildDir, "src/" + suite.getName() + "/cunitLauncher");
                    launcherSources.getSource().srcDir(new File(baseDir, "c"));
                    launcherSources.getExportedHeaders().srcDir(new File(baseDir, "headers"));
                }
            });

            suite.getSources().withType(CSourceSet.class).named("c", new Action<CSourceSet>() {
                @Override
                public void execute(CSourceSet cSourceSet) {
                    cSourceSet.lib(suite.getSources().get(CUNIT_LAUNCHER_SOURCE_SET));
                }
            });
        }

        @Mutate
        public void createCUnitLauncherTasks(TaskContainer tasks, TestSuiteContainer testSuites) {
            for (final CUnitTestSuiteSpec suite : testSuites.withType(CUnitTestSuiteSpec.class).values()) {

                String taskName = suite.getName() + "CUnitLauncher";
                GenerateCUnitLauncher skeletonTask = tasks.create(taskName, GenerateCUnitLauncher.class);

                CSourceSet launcherSources = findLauncherSources(suite);
                skeletonTask.setSourceDir(launcherSources.getSource().getSrcDirs().iterator().next());
                skeletonTask.setHeaderDir(launcherSources.getExportedHeaders().getSrcDirs().iterator().next());
                launcherSources.builtBy(skeletonTask);
            }
        }

        private CSourceSet findLauncherSources(CUnitTestSuiteSpec suite) {
            return suite.getSources().withType(CSourceSet.class).get(CUNIT_LAUNCHER_SOURCE_SET);
        }

        @ComponentType
        public void registerCUnitTestBinaryType(TypeBuilder<CUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultCUnitTestSuiteBinary.class);
        }

        @ComponentBinaries
        public void createCUnitTestBinaries(ModelMap<CUnitTestSuiteBinarySpec> binaries,
                                            CUnitTestSuiteSpec testSuite,
                                            @Path("buildDir") final File buildDir,
                                            final ServiceRegistry serviceRegistry,
                                            final ITaskFactory taskFactory) {
            createNativeTestSuiteBinaries(binaries, testSuite, CUnitTestSuiteBinarySpec.class, "CUnitExe", buildDir, serviceRegistry);
        }
    }

}
