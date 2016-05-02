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
package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.util.concurrent.PollingConditions

class NonInteractiveLaunchIntegrationTest extends AbstractIntegrationSpec {

    private prepareWrapper() {
        buildFile << """
wrapper {
    gradleVersion = '2.13'
}

task hello << {
    file('hello.txt').createNewFile()
}

defaultTasks 'hello'
"""

        executer.withTasks('wrapper').run()
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "can execute from Finder"() {
        def conditions = new PollingConditions(timeout: 60, initialDelay: 10, delay: 1)

        given:
        prepareWrapper()
        File gradlew = file("gradlew")

        expect:
        gradlew.exists()

        when:
        // use the open program since that is what Finder uses
        // set the cwd to the testDirectory to reproduce where gradle normally is run from
        ["/usr/bin/open", "-g", gradlew.absolutePath].execute([], testDirectory)

        then:
        // the hello task writes a file named "hello.txt" to the project dir
        // stdout is not used for the test because the open program does not provide stdout
        // the process is forked so we just have to wait a bit
        conditions.eventually {
            assert file("hello.txt").exists()
        }
    }
}
