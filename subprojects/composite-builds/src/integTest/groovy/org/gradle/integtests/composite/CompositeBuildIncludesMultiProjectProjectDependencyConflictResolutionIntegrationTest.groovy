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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.resolve.AbstractProjectDependencyConflictResolutionIntegrationSpec
import org.gradle.internal.build.BuildState
import org.gradle.test.fixtures.file.TestFile

/**
 * This is a variation of {@link org.gradle.integtests.resolve.MultiProjectProjectDependencyConflictResolutionIntegrationTest}
 * where the multi-project build is included in a composite build that steers the build process. By this, the automatic substitution behavior of
 * replacing a binary dependency with an included project dependency (if available) independent of versions, is applied in the whole build.
 * This yields a slightly different conflict resolution behavior compared to executing the multi-project build independently.
 * See: 'winner' vs 'winnerAutoSubstitution' in {@link AbstractProjectDependencyConflictResolutionIntegrationSpec}.
 *
 * Hence: {@link CompositeBuildIncludesMultiProjectProjectDependencyConflictResolutionIntegrationTest#isAutoDependencySubstitution()} is 'true'
 */
class CompositeBuildIncludesMultiProjectProjectDependencyConflictResolutionIntegrationTest extends AbstractProjectDependencyConflictResolutionIntegrationSpec {

    BuildTestFile multiProject

    def setup() {
        buildTestFixture.withBuildInSubDir()
        multiProject = buildTestFixture.populate('multiProject') {
            buildFile << checkHelper(buildId, projectPath)
        }
        super.settingsFile << "includeBuild 'multiProject'\n";
    }

    @Override
    protected TestFile getSettingsFile() {
        return multiProject.settingsFile;
    }

    @Override
    String getIncludeMechanism() {
        "include"
    }

    @Override
    String getBuildId() {
        "((${ProjectInternal.name}) project).getServices().get(${BuildState.name}.class).getBuildIdentifier()"
    }

    @Override
    String getProjectPath() {
        "':' + projectName"
    }

    @Override
    String dependsOnMechanism(String projectName, String taskName) {
        "gradle.includedBuild('multiProject').task(':$projectName:$taskName')"
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
        multiProject.buildFile << "project(':$name') {"
        multiProject.buildFile << definition
        multiProject.buildFile << "}"
    }

    @Override
    boolean isAutoDependencySubstitution() {
        true
    }
}
