/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.buildevents

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildFailureIntegrationTest extends AbstractIntegrationSpec {
    def "still prints errors when exception misbehaves"() {
        // When running in-process, the NPE propagates out of the test fixtures
        executer.requireIsolatedDaemons()
        executer.requireDaemon()

        buildFile << """
class BadException extends Exception {
   String getMessage() {
     throw new NullPointerException()
   }
}

throw new BadException()
"""

        when:
        fails("help", "--stacktrace")
        then:
        failure.assertHasDescription("A problem occurred evaluating root project")
        failure.assertHasCause("Unable to get message for failure of type BadException due to null")
    }
}
