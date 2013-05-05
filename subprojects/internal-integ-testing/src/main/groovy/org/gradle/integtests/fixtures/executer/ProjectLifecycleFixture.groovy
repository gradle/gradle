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

package org.gradle.integtests.fixtures.executer

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

import static java.util.Arrays.asList

class ProjectLifecycleFixture extends InitScriptExecuterFixture {

    private TestFile fixtureData

    ProjectLifecycleFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    List<String> configuredProjects

    String initScriptContent() {
        fixtureData = testDir.testDirectory.file("lifecycle-fixture-data.txt")
        """File outputFile = file("${fixtureData.toURI()}")
           def listener = new ProjectEvaluationListener() {
                void afterEvaluate(Project project, ProjectState state) {
                    outputFile << project.path + ";"
                }
                void beforeEvaluate(Project project) {}
           }
           gradle.addListener(listener)
           buildFinished {
               gradle.removeListener(listener)
           }"""
    }

    void afterBuild() {
        configuredProjects = asList(fixtureData.text.split(";"))
        assert fixtureData.delete()
    }

    void assertProjectsConfigured(String ... projectPaths) {
        assert configuredProjects == projectPaths
    }
}