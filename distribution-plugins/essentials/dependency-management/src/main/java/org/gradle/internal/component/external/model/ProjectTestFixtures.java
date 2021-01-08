/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component.external.model;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;

import static org.gradle.internal.component.external.model.TestFixturesSupport.TEST_FIXTURES_FEATURE_NAME;

public class ProjectTestFixtures implements Action<ModuleDependencyCapabilitiesHandler> {
    private final Project project;

    public ProjectTestFixtures(Project project) {
        this.project = project;
    }

    @Override
    public void execute(ModuleDependencyCapabilitiesHandler capabilities) {
        capabilities.requireCapability(new ProjectDerivedCapability(project, TEST_FIXTURES_FEATURE_NAME));
    }
}
