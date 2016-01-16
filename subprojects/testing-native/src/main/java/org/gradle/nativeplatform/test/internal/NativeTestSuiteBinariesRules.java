/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.test.internal;

import org.gradle.api.Project;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.internal.BinaryNamingScheme;

import static org.gradle.model.internal.core.ModelNodes.withType;
import static org.gradle.model.internal.core.NodePredicate.allDescendants;

@SuppressWarnings("unused")
public class NativeTestSuiteBinariesRules extends RuleSource {

    public static <T extends NativeTestSuiteBinarySpec> void apply(ModelRegistry registry, Class<T> testSuiteBinaryClass) {
        registry.getRoot().applyTo(allDescendants(withType(testSuiteBinaryClass)), NativeTestSuiteBinariesRules.class);
    }

    @Mutate
    public void configureRunTask(final NativeTestSuiteBinarySpecInternal testSuiteBinary) {
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
