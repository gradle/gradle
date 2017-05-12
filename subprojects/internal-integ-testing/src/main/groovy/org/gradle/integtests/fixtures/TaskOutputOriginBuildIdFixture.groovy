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

class TaskOutputOriginBuildIdFixture extends UserInitScriptExecuterFixture {

    Map<String, UniqueId> originIds = [:]

    TaskOutputOriginBuildIdFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    private TestFile getFile() {
        testDir.testDirectory.file("originIds.json")
    }

    @Override
    String initScriptContent() {
        """
            if (gradle.parent == null) {
                def ids = Collections.synchronizedMap([:])
                gradle.ext.originIds = ids
                gradle.buildFinished {
                    gradle.rootProject.file("${TextUtil.normaliseFileSeparators(file.absolutePath)}").text = groovy.json.JsonOutput.toJson(ids)
                }
            }
        
            def rootGradle = gradle
            while (rootGradle.parent != null) {
                rootGradle = rootGradle.parent
            }
            
            gradle.taskGraph.afterTask {
                rootGradle.ext.originIds[it.identityPath] = it.state.originBuildId?.asString()
            }
        """
    }

    @Override
    void afterBuild() {
        def rawOriginIds = new JsonSlurper().parse(file) as Map<String, String>
        originIds.clear()
        rawOriginIds.each {
            originIds[it.key] = it.value == null ? null : UniqueId.from(it.value)
        }
    }

    UniqueId originId(String path) {
        def tasks = originIds.keySet()
        assert path in tasks
        originIds[path]
    }

}
