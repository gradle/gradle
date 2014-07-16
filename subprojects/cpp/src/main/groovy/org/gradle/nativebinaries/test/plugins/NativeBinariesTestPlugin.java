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

package org.gradle.nativebinaries.test.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.Finalize;
import org.gradle.model.RuleSource;
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal;
import org.gradle.nativebinaries.plugins.NativeComponentPlugin;
import org.gradle.nativebinaries.tasks.InstallExecutable;
import org.gradle.nativebinaries.test.ProjectNativeTestSuiteBinary;
import org.gradle.nativebinaries.test.internal.DefaultTestSuiteContainer;
import org.gradle.nativebinaries.test.tasks.RunTestExecutable;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.internal.BinaryNamingScheme;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;

    @Inject
    public NativeBinariesTestPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeComponentPlugin.class);
        project.getExtensions().create("testSuites", DefaultTestSuiteContainer.class, instantiator);
    }

    /**
     * Model rules.
     */
    @RuleSource
    public static class Rules {
        @Finalize
        public void createTestTasks(final TaskContainer tasks, BinaryContainer binaries) {

            // TODO:DAZ Use simple iteration
            binaries.withType(ProjectNativeTestSuiteBinary.class).all(new Action<ProjectNativeTestSuiteBinary>() {
                public void execute(ProjectNativeTestSuiteBinary testBinary) {
                    ProjectNativeBinaryInternal binary = (ProjectNativeBinaryInternal) testBinary;
                    final BinaryNamingScheme namingScheme = binary.getNamingScheme();

                    RunTestExecutable runTask = tasks.create(namingScheme.getTaskName("run"), RunTestExecutable.class);
                    final Project project = runTask.getProject();
                    runTask.setDescription(String.format("Runs the %s", binary.getNamingScheme().getDescription()));

                    final InstallExecutable installTask = binary.getTasks().withType(InstallExecutable.class).iterator().next();
                    runTask.getInputs().files(installTask.getOutputs().getFiles());
                    runTask.setTestExecutable(installTask.getRunScript());
                    runTask.setOutputDir(new File(project.getBuildDir(), "/test-results/" + namingScheme.getOutputDirectoryBase()));
                }
            });
        }
    }
}
