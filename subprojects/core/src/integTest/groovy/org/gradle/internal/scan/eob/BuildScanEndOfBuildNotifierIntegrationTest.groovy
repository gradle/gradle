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

package org.gradle.internal.scan.eob

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

@Unroll
class BuildScanEndOfBuildNotifierIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            def notifier = services.get(${BuildScanEndOfBuildNotifier.name})
        """
    }

    def "can observe successful build"() {
        when:
        buildFile << """
            notifier.notify {
                println "failure is null: \${it.failure == null}" 
            }
        """

        run()

        then:
        output.contains("failure is null: true")
    }

    def "can observe failed build"() {
        when:
        buildFile << """
            task t { doFirst { throw new Exception("!") } }
            notifier.notify {
                println "failure message: \${it.failure.cause.message}" 
            }
        """

        runAndFail("t")

        then:
        output.contains("failure message: Execution failed for task ':t'.")
    }

    def "can only register one listener"() {
        when:
        buildFile << """
            notifier.notify { }
            notifier.notify { }
        """

        def failure = runAndFail()

        then:
        failure.assertHasCause("Listener already registered")
    }
}
