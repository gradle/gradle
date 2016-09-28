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

import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.file.TestFile
/**
 * Tests for composite build.
 */
abstract class AbstractCompositeBuildIntegrationTest extends AbstractIntegrationSpec {
    BuildTestFile buildA
    List includedBuilds = []

    def setup() {
        buildTestFixture.withBuildInSubDir()
        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
"""
        }
    }

    def dependency(BuildTestFile sourceBuild = buildA, String notation) {
        sourceBuild.buildFile << """
            dependencies {
                compile '${notation}'
            }
"""
    }

    def includeBuild(File build, def mappings = "") {
        if (mappings == "") {
            buildA.settingsFile << """
                includeBuild('${build.toURI()}')
"""
        } else {
            buildA.settingsFile << """
                includeBuild('${build.toURI()}') {
                    dependencySubstitution {
                        $mappings
                    }
                }
"""

        }
    }

    protected void execute(BuildTestFile build, String task, Iterable<String> arguments = []) {
        prepare(build, arguments)
        succeeds(task)
    }

    protected void fails(BuildTestFile build, String task, Iterable<String> arguments = []) {
        prepare(build, arguments)
        fails(task)
    }

    private void prepare(BuildTestFile build, Iterable<String> arguments) {
        executer.inDirectory(build)

        List<File> includedBuilds = Lists.newArrayList(includedBuilds)
        includedBuilds.each {
            includeBuild(it)
        }
        for (String arg : arguments) {
            executer.withArgument(arg)
        }
    }

    protected void executed(String... tasks) {
        for (String task : tasks) {
            executedOnce(task)
        }
    }

    protected void executedOnce(String task) {
        def executedTasks = result.executedTasks
        assert executedTasks.contains(task)
        assert executedTasks.findAll({ it == task }).size() == 1
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }
}
