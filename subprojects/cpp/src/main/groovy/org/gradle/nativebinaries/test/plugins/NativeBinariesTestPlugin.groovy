/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.test.plugins
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.BinaryContainer
import org.gradle.model.ModelRules
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import org.gradle.nativebinaries.plugins.NativeBinariesPlugin
import org.gradle.nativebinaries.tasks.InstallExecutable
import org.gradle.nativebinaries.test.TestSuiteExecutableBinary
import org.gradle.nativebinaries.test.internal.DefaultTestSuiteContainer
import org.gradle.nativebinaries.test.tasks.RunTestExecutable

import javax.inject.Inject

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator
    private final ModelRules modelRules
    private final NativeDependencyResolver resolver

    @Inject
    public NativeBinariesTestPlugin(Instantiator instantiator, ModelRules modelRules, NativeDependencyResolver resolver) {
        this.instantiator = instantiator
        this.modelRules = modelRules
        this.resolver = resolver
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeBinariesPlugin.class)

        project.getExtensions().create(
                "testSuites",
                DefaultTestSuiteContainer.class,
                instantiator
        );

        final BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);
        binaries.withType(TestSuiteExecutableBinary).all { testBinary ->
            def binary = testBinary as ProjectNativeBinaryInternal
            def namingScheme = binary.namingScheme

            // TODO:DAZ Need a better model for accessing tasks related to binary
            def installTask = binary.tasks.withType(InstallExecutable).find()

            def runTask = project.tasks.create(namingScheme.getTaskName("run"), RunTestExecutable)
            runTask.setDescription("Runs the " + binary.getDisplayName())
            runTask.inputs.files(installTask.outputs.files)

            runTask.conventionMapping.testExecutable = { installTask.runScript }
            runTask.conventionMapping.outputDir = { project.file("${project.buildDir}/test-results/${namingScheme.outputDirectoryBase}") }
        }
    }
}