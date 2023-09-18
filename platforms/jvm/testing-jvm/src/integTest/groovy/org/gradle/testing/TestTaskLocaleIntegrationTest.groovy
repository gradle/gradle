/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class TestTaskLocaleIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/2661")
    def "test logging can be configured on turkish locale"() {
        given:
        buildFile << """
            apply plugin:'java'
            test {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """.stripIndent()

        when:
        executer
            .requireDaemon()
            .requireIsolatedDaemons()
            .withBuildJvmOpts("-Duser.language=tr", "-Duser.country=TR")
            .withTasks("help")
            .run()

        then:
        noExceptionThrown()
    }
}
