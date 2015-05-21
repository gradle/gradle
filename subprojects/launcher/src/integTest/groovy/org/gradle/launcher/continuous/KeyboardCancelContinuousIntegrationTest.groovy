/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import spock.lang.Ignore

class KeyboardCancelContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def setup() {
        buildFile << "apply plugin: 'java'"
    }

    @Ignore("implementation doesn't actually test what it says it does")
    def "should not cancel build when System.in immediately returns EOF"() {
        when:
        succeeds("build")
        then:
        expectOutput {
            !it.contains("Build cancelled")
        }
    }

    def "should cancel build when System.in contains CTRL+D"() {
        given:
        succeeds("build")
        when:
        emulateCtrlD()
        then:
        expectOutput {
            it.contains("Build cancelled")
        }
    }

    def "should cancel build when System.in is closed"() {
        given:
        succeeds("build")
        when:
        stdinPipe.close()
        stdinPipe = null
        then:
        expectOutput {
            it.contains("Build cancelled")
        }
    }

    def "should cancel build when System.in contains some other characters, then CTRL+D"() {
        when:
        succeeds("build")
        stdinPipe << 'abc'
        then:
        expectOutput(0.5) {
            !it.contains("Build cancelled")
        }
        when:
        emulateCtrlD()
        then:
        expectOutput {
            it.contains("Build cancelled")
        }
    }
}
