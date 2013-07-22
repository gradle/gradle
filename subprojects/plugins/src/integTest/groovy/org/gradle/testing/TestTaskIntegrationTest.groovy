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

package org.gradle.testing;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue;

public class TestTaskIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2702")
    def "should not resolve configurations when there are no tests"() {
        buildFile << """
            apply plugin: 'java'

            configure([configurations.testRuntime, configurations.testCompile]) {
                incoming.beforeResolve { assert false : "should not be resolved" }
            }
        """

        when:
        run("build")

        then:
        noExceptionThrown()
    }

    def "test task is skipped when there are no tests"() {
        buildFile << "apply plugin: 'java'"
        file("src/test/java/not_a_test.txt")

        when:
        run("build")

        then:
        result.assertTaskSkipped(":test")
    }
}
