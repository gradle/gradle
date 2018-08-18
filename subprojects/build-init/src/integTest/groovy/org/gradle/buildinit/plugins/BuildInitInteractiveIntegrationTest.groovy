/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.TextUtil

class BuildInitInteractiveIntegrationTest extends AbstractInitIntegrationSpec {
    def "prompts user when run from an interactive session"() {
        when:
        executer.withForceInteractive(true)
        executer.withStdinPipe()
        executer.withTasks("init")
        def handle = executer.start()

        // Select 'basic'
        ConcurrentTestUtil.poll(20) {
            assert handle.standardOutput.contains("Select type of project to generate:")
        }
        handle.stdinPipe.write(("1" + TextUtil.platformLineSeparator).bytes)

        // Select 'kotlin'
        ConcurrentTestUtil.poll {
            assert handle.standardOutput.contains("Select build script DSL:")
        }
        handle.stdinPipe.write(("2" + TextUtil.platformLineSeparator).bytes)

        // Select default project name
        ConcurrentTestUtil.poll {
            assert handle.standardOutput.contains("Project name (default: $testDirectory.name)")
        }
        handle.stdinPipe.write(TextUtil.platformLineSeparator.bytes)
        handle.stdinPipe.close()
        handle.waitForFinish()

        then:
        dslFixtureFor(BuildInitDsl.KOTLIN).assertGradleFilesGenerated()
    }
}
