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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Timeout

@Requires(TestPrecondition.JDK6)
class Jdk6EnablingContinuousModeExecutionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireGradleHome()
    }

    @Timeout(60)
    def "can NOT enable continuous mode"() {
        when:
        executer.withArgument("--watch")

        then:
        fails "tasks"
        errorOutput.contains "Continuous mode (--watch) is not supported on versions of Java older than 1.7."
    }

}
