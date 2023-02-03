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

package org.gradle.integtests.resolve

import org.gradle.api.internal.project.ProjectInternal

class MultiProjectProjectDependencyConflictResolutionIntegrationTest extends AbstractProjectDependencyConflictResolutionIntegrationSpec {

    @Override
    String getIncludeMechanism() {
        "include"
    }

    @Override
    String getBuildId() {
        "((${ProjectInternal.name}) project).getOwner().getOwner().getBuildIdentifier()"
    }

    @Override
    String getProjectPath() {
        "':' + projectName"
    }

    @Override
    String dependsOnMechanism(String projectName, String taskName) {
        "'$projectName:$taskName'"
    }

    @Override
    String declareDependency(String moduleName, String moduleVersion) {
        "project(':$moduleName')"
    }

    @Override
    String declaredDependencyId(String moduleName, String moduleVersion) {
        "projectId('$moduleName')"
    }

    @Override
    void moduleDefinition(String name, String definition) {
        buildFile << "project(':$name') {"
        buildFile << definition
        buildFile << "}"
    }

    @Override
    boolean isAutoDependencySubstitution() {
        false
    }
}
