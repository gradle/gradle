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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.TestSuiteContainer;
import org.gradle.nativeplatform.test.internal.DefaultTestSuiteContainer;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import java.io.File;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<ProjectInternal> {
    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    public static class Rules {
        @Model
        TestSuiteContainer testSuites(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultTestSuiteContainer.class, instantiator);
        }

        @Finalize // Must run after test binaries have been created (currently in CUnit plugin)
        void attachTestedBinarySourcesToTestBinaries(BinaryContainer binaries) {
            for (NativeTestSuiteBinarySpec testSuiteBinary : binaries.withType(NativeTestSuiteBinarySpec.class)) {
                NativeBinarySpec testedBinary = testSuiteBinary.getTestedBinary();
                testSuiteBinary.source(testedBinary.getSource());

                for (DependentSourceSet testSource : testSuiteBinary.getSource().withType(DependentSourceSet.class)) {
                    testSource.lib(testedBinary.getSource());
                }
            }
        }

        @Finalize
        public void createTestTasks(final TaskContainer tasks, BinaryContainer binaries) {
            for (NativeTestSuiteBinarySpec testBinary : binaries.withType(NativeTestSuiteBinarySpec.class)) {
                NativeBinarySpecInternal binary = (NativeBinarySpecInternal) testBinary;
                final BinaryNamingScheme namingScheme = binary.getNamingScheme();

                RunTestExecutable runTask = tasks.create(namingScheme.getTaskName("run"), RunTestExecutable.class);
                final Project project = runTask.getProject();
                runTask.setDescription(String.format("Runs the %s", binary.getNamingScheme().getDescription()));

                final InstallExecutable installTask = binary.getTasks().withType(InstallExecutable.class).iterator().next();
                runTask.getInputs().files(installTask.getOutputs().getFiles());
                runTask.setExecutable(installTask.getRunScript().getPath());
                runTask.setOutputDir(new File(project.getBuildDir(), "/test-results/" + namingScheme.getOutputDirectoryBase()));
            }
        }
    }
}
