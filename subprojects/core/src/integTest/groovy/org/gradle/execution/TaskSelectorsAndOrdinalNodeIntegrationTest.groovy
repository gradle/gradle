/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import spock.lang.Issue
import spock.lang.Timeout

class TaskSelectorsAndOrdinalNodeIntegrationTest extends AbstractIntegrationSpec {
    /**
     * If this build starts to time out, something is seriously wrong.
     *
     * This build should complete in ~seconds.
     */
    @Timeout(60)
    @Issue("https://github.com/gradle/gradle/issues/20741")
    @ToBeFixedForIsolatedProjects(because = "subprojects")
    def "build is not exponentially slower when many tasks are requested"() {
        createDirs((1..30).collect({ "sub" + it }) as String[])
        settingsFile << """
            rootProject.name = "test"
            (1..30).each {
                include "sub" + it
            }
        """
        buildFile << """
            subprojects {
                apply plugin: 'java'
            }
        """
        expect:
        succeeds("clean", *(1..30).collect { "sub$it:assemble" })
    }
}
