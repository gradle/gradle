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

package org.gradle.integtests.composite

import org.gradle.integtests.resolve.AbstractProjectDependencyConflictResolutionIntegrationSpec

class CompositeBuildProjectDependencyConflictResolutionIntegrationTest extends AbstractProjectDependencyConflictResolutionIntegrationSpec {

    @Override
    String getIncludeMechanism() {
        "includeBuild"
    }

    @Override
    String getBuildId() {
        "new org.gradle.api.internal.artifacts.DefaultBuildIdentifier(org.gradle.util.Path.path(':' + projectName))"
    }

    @Override
    String getProjectPath() {
        "':'"
    }

    @Override
    String dependsOnMechanism(String projectName, String taskName) {
        "gradle.includedBuild('$projectName').task(':$taskName')"
    }

    @Override
    String declareDependency(String moduleName, String moduleVersion) {
        "'myorg:$moduleName:$moduleVersion'"
    }

    @Override
    String declaredDependencyId(String moduleName, String moduleVersion) {
        "moduleId('myorg', '$moduleName', '$moduleVersion')"
    }

    @Override
    void moduleDefinition(String name, String definition) {
        buildTestFixture.populate(name) {
            buildFile << definition
            buildFile << checkHelper(buildId, projectPath)
        }
    }

    @Override
    boolean isAutoDependencySubstitution() {
        true
    }
}
