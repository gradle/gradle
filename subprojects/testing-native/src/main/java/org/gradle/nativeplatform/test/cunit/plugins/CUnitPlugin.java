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
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.c.CSourceSet;
import org.gradle.language.c.plugins.CLangPlugin;
import org.gradle.model.*;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteBinary;
import org.gradle.nativeplatform.test.cunit.internal.DefaultCUnitTestSuiteSpec;
import org.gradle.nativeplatform.test.cunit.tasks.GenerateCUnitLauncher;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.platform.base.test.TestSuiteContainer;

import java.io.File;

import static org.gradle.nativeplatform.test.internal.NativeTestSuites.createNativeTestSuiteBinaries;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class CUnitPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        private static final String CUNIT_LAUNCHER_SOURCE_SET = "cunitLauncher";

        // TODO:DAZ Test suites should belong to ComponentSpecContainer, and we could rely on more conventions from the base plugins
        @Defaults
        public void createCUnitTestSuitePerComponent(TestSuiteContainer testSuites, ModelMap<NativeComponentSpec> components) {
            for (final NativeComponentSpec component : components.values()) {
                final String suiteName = String.format("%sTest", component.getName());
                testSuites.create(suiteName, CUnitTestSuiteSpec.class, new Action<CUnitTestSuiteSpec>() {
                    @Override
                    public void execute(CUnitTestSuiteSpec testSuite) {
                        testSuite.setTestedComponent(component);
                    }
                });
            }
        }
        
        @ComponentType
        public void registerCUnitTestSuiteSpecType(ComponentTypeBuilder<CUnitTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultCUnitTestSuiteSpec.class);
        }

        @Finalize
        public void configureCUnitTestSuiteSources(TestSuiteContainer testSuites, @Path("buildDir") final File buildDir) {

            for (final CUnitTestSuiteSpec suite : testSuites.withType(CUnitTestSuiteSpec.class).values()) {
                suite.getSources().create(CUNIT_LAUNCHER_SOURCE_SET, CSourceSet.class, new Action<CSourceSet>() {
                    @Override
                    public void execute(CSourceSet launcherSources) {
                        File baseDir = new File(buildDir, String.format("src/%s/cunitLauncher", suite.getName()));
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

        @BinaryType
        public void registerCUnitTestBinaryType(BinaryTypeBuilder<CUnitTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultCUnitTestSuiteBinary.class);
        }

        @Mutate
        public void createCUnitTestBinaries(TestSuiteContainer testSuites, @Path("buildDir") final File buildDir,
                                            LanguageTransformContainer languageTransforms, final ServiceRegistry serviceRegistry, final ITaskFactory taskFactory) {
            createNativeTestSuiteBinaries(testSuites, CUnitTestSuiteSpec.class, CUnitTestSuiteBinarySpec.class, "CUnitExe", buildDir, serviceRegistry);
        }

    }

}
