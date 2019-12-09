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

package org.gradle.integtests.tooling.r61

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.model.gradle.GradleBuild

@TargetGradleVersion("<6.1")
class InvalidateVirtualFileSystemOldVersionsCrossVersionSpec extends AbstractInvalidateVirtualFileSystemCrossVersionSpec {

    def "invalidating paths has no effect"() {
        when:
        createIdleDaemon()
        def executedCommandCount = countExecutedCommands()

        then:
        executedCommandCount == 3

        when:
        withConnection { connection ->
            connection.notifyDaemonsAboutChangedPaths(toPaths(changedPaths))
        }

        then:
        !pathsInvalidated()
        countExecutedCommands() == executedCommandCount
    }

    private int countExecutedCommands() {
        toolingApi.daemons.daemon.log.count("command = ")
    }

    private void createIdleDaemon() {
        withConnection { connection ->
            connection.model(GradleBuild).get()
        }
        toolingApi.daemons.daemon.assertIdle()
    }
}
