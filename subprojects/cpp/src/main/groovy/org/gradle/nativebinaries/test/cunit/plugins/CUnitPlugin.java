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
package org.gradle.nativebinaries.test.cunit.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.model.ModelRules;
import org.gradle.nativebinaries.test.cunit.internal.ConfigureCUnitTestSources;
import org.gradle.nativebinaries.test.cunit.internal.CreateCUnitTestSuites;
import org.gradle.nativebinaries.test.plugins.NativeBinariesTestPlugin;

import javax.inject.Inject;

/**
 * A plugin that sets up the infrastructure for testing native binaries with CUnit.
 */
@Incubating
public class CUnitPlugin implements Plugin<ProjectInternal> {

    private final ModelRules modelRules;

    @Inject
    public CUnitPlugin(ModelRules modelRules) {
        this.modelRules = modelRules;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(NativeBinariesTestPlugin.class);

        modelRules.config("testSuites", new CreateCUnitTestSuites(project));
        modelRules.rule(new ConfigureCUnitTestSources(project));
    }
}