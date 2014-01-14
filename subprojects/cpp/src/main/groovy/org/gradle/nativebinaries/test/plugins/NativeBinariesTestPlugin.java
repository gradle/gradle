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
package org.gradle.nativebinaries.test.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.ModelRules;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.plugins.NativeBinariesModelPlugin;
import org.gradle.nativebinaries.test.internal.CreateTestBinaries;
import org.gradle.nativebinaries.test.internal.CreateTestTasks;
import org.gradle.nativebinaries.test.internal.DefaultTestSuiteContainer;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class NativeBinariesTestPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final ModelRules modelRules;
    private final NativeDependencyResolver resolver;

    @Inject
    public NativeBinariesTestPlugin(Instantiator instantiator, ModelRules modelRules, NativeDependencyResolver resolver) {
        this.instantiator = instantiator;
        this.modelRules = modelRules;
        this.resolver = resolver;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeBinariesModelPlugin.class);

        File buildDir = project.getBuildDir();

        modelRules.register("testSuites", new DefaultTestSuiteContainer(instantiator));
        modelRules.rule(new CreateTestBinaries(instantiator, resolver, buildDir));
        modelRules.rule(new CreateTestTasks(project));
    }
}