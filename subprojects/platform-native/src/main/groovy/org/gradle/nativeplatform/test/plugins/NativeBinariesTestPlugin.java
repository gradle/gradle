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

package org.gradle.nativeplatform.test.plugins;

import org.gradle.api.*;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.model.*;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.test.TestSuiteContainer;
import org.gradle.platform.base.test.TestSuiteSpec;

/**
 * A plugin that sets up the infrastructure for testing native binaries.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        void testSuites(TestSuiteContainer testSuites) {}

        @Defaults
        void attachTestedBinarySourcesToTestBinaries(ModelMap<BinarySpec> binaries) {
            binaries.withType(NativeTestSuiteBinarySpecInternal.class).afterEach(new Action<NativeTestSuiteBinarySpecInternal>() {
                @Override
                public void execute(NativeTestSuiteBinarySpecInternal testSuiteBinary) {
                    BinarySpec testedBinary = testSuiteBinary.getTestedBinary();
                    for (DependentSourceSet testSource : testSuiteBinary.getInputs().withType(DependentSourceSet.class)) {
                        testSource.lib(testedBinary.getInputs());
                    }
                    testSuiteBinary.getInputs().addAll(testedBinary.getInputs());
                }
            });
        }

        @Mutate
        public void createTestTasks(final TaskContainer tasks, ModelMap<NativeTestSuiteBinarySpec> binaries) {
            for (NativeTestSuiteBinarySpec testBinary : binaries) {
                NativeBinarySpecInternal binary = (NativeBinarySpecInternal) testBinary;
                final BinaryNamingScheme namingScheme = binary.getNamingScheme();

                RunTestExecutable runTask = tasks.create(namingScheme.getTaskName("run"), RunTestExecutable.class);
                final Project project = runTask.getProject();
                runTask.setDescription(String.format("Runs the %s", binary));

                final InstallExecutable installTask = binary.getTasks().withType(InstallExecutable.class).iterator().next();
                runTask.getInputs().files(installTask.getOutputs().getFiles());
                runTask.setExecutable(installTask.getRunScript().getPath());
                runTask.setOutputDir(namingScheme.getOutputDirectory(project.getBuildDir(), "test-results"));

                testBinary.getTasks().add(runTask);
            }
        }

        @Defaults
        public void copyTestBinariesToGlobalContainer(ModelMap<BinarySpec> binaries, TestSuiteContainer testSuites) {
            for (TestSuiteSpec testSuite : testSuites.withType(TestSuiteSpec.class).values()) {
                for (BinarySpec binary : testSuite.getBinaries().values()) {
                    binaries.put(((BinarySpecInternal) binary).getProjectScopedName(), binary);
                }
            }
        }

        @Mutate
        void attachBinariesToCheckLifecycle(ModelMap<Task> tasks, final ModelMap<NativeTestSuiteBinarySpec> binaries) {
            // TODO - binaries aren't an input to this rule, they're an input to the action
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, new Action<Task>() {
                @Override
                public void execute(Task checkTask) {
                    for (NativeTestSuiteBinarySpec testBinary : binaries) {
                        checkTask.dependsOn(testBinary.getTasks().getRun());
                    }
                }
            });
        }
    }

}
