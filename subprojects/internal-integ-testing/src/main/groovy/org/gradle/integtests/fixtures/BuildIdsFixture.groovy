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

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.UserInitScriptExecuterFixture
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.internal.id.UniqueId
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

/**
 * Extracts the build ID for a build, and asserts that all nested builds have the same ID.
 */
class BuildIdsFixture extends UserInitScriptExecuterFixture {

    private final List<UniqueId> ids = []

    BuildIdsFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    UniqueId getBuildId() {
        ids.last()
    }

    List<UniqueId> getBuildIds() {
        new ArrayList<UniqueId>(ids)
    }

    private TestFile getFile() {
        testDir.testDirectory.file("buildId.txt")
    }

    @Override
    String initScriptContent() {
        """
            if (gradle.parent == null) {
                gradle.rootProject {
                    it.file("${TextUtil.normaliseFileSeparators(file.absolutePath)}").text = gradle.buildIds.buildId
                }            
            } else {
                assert gradle.buildIds.buildId == gradle.parent.buildIds.buildId
            }   
        """
    }

    @Override
    void afterBuild() {
        ids << UniqueId.from(file.text)
        assert ids.unique() == ids
    }

}
