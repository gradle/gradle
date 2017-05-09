/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.UserInitScriptExecuterFixture
import org.gradle.internal.id.UniqueId
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

/**
 * Extracts the build ID for a build, and asserts that all nested builds have the same ID.
 */
class BuildIdsFixture extends UserInitScriptExecuterFixture {

    private final List<UniqueId> ids = []
    private final List<List<String>> buildPaths = []

    BuildIdsFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    UniqueId getBuildId() {
        ids.last()
    }

    List<UniqueId> getBuildIds() {
        new ArrayList<UniqueId>(ids)
    }

    List<List<String>> getBuildPaths() {
        new ArrayList<List<String>>(buildPaths)
    }

    List<String> lastBuildPaths() {
        getBuildPaths().last()
    }

    private TestFile getBuildIdFile() {
        testDir.testDirectory.file("buildId.txt")
    }

    private TestFile getBuildPathsFile() {
        testDir.testDirectory.file("buildPaths.json")
    }

    @Override
    String initScriptContent() {
        """
            if (gradle.parent == null) {
                gradle.rootProject {
                    it.file("${TextUtil.normaliseFileSeparators(buildIdFile.absolutePath)}").text = gradle.buildIds.buildId
                }        
                
                def buildPaths = Collections.synchronizedList([])
                gradle.ext.buildPaths = buildPaths
                gradle.buildFinished {
                    gradle.rootProject.file("${TextUtil.normaliseFileSeparators(buildPathsFile.absolutePath)}").text = groovy.json.JsonOutput.toJson(buildPaths)
                }
            } else {
                assert gradle.buildIds.buildId == gradle.parent.buildIds.buildId
            }

            gradle.rootProject {
                def rootGradle = gradle
                while (rootGradle.parent != null) {
                    rootGradle = rootGradle.parent
                }           
                rootGradle.ext.buildPaths << gradle.identityPath.toString()
            }
        """
    }

    @Override
    void afterBuild() {
        ids << UniqueId.from(buildIdFile.text)
        assert ids.unique() == ids

        def paths = new JsonSlurper().parse(buildPathsFile) as List<String>
        buildPaths << paths.sort()
    }

}
