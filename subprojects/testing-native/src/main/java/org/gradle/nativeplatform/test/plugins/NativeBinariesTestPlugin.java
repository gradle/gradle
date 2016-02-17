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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.model.Each;
import org.gradle.model.Finalize;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.plugins.NativeComponentPlugin;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.internal.DefaultNativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.testing.base.plugins.TestingModelBasePlugin;

/**
 * A plugin that sets up the infrastructure for testing native binaries.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeComponentPlugin.class);
        project.getPluginManager().apply(TestingModelBasePlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @ComponentType
        void nativeTestSuiteBinary(TypeBuilder<NativeTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultNativeTestSuiteBinarySpec.class);
            builder.internalView(NativeTestSuiteBinarySpecInternal.class);
        }

        @Finalize
        void attachTestedBinarySourcesToTestBinaries(@Each final NativeTestSuiteBinarySpecInternal testSuiteBinary) {
            final BinarySpec testedBinary = testSuiteBinary.getTestedBinary();
            testSuiteBinary.getInputs().withType(DependentSourceSet.class).all(new Action<DependentSourceSet>() {
                @Override
                public void execute(DependentSourceSet testSource) {
                    testSource.lib(testedBinary.getInputs());
                }
            });
            testedBinary.getInputs().all(new Action<LanguageSourceSet>() {
                @Override
                public void execute(LanguageSourceSet testedSource) {
                    testSuiteBinary.getInputs().add(testedSource);
                }
            });
        }

        @Finalize
        void configureRunTask(@Each NativeTestSuiteBinarySpecInternal testSuiteBinary) {
            BinaryNamingScheme namingScheme = testSuiteBinary.getNamingScheme();
            NativeTestSuiteBinarySpec.TasksCollection tasks = testSuiteBinary.getTasks();
            InstallExecutable installTask = (InstallExecutable) tasks.getInstall();
            RunTestExecutable runTask = (RunTestExecutable) tasks.getRun();
            runTask.getInputs().files(installTask.getOutputs().getFiles());
            runTask.setExecutable(installTask.getRunScript().getPath());
            Project project = runTask.getProject();
            runTask.setOutputDir(namingScheme.getOutputDirectory(project.getBuildDir(), "test-results"));
        }
    }
}
