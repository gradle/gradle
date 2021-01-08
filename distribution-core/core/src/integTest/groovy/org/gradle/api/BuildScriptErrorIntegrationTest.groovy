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

package org.gradle.api
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.CoreMatchers.containsString

class BuildScriptErrorIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        settingsFile << "rootProject.name = 'ProjectError'"
    }

    def "produces reasonable error message when buildFile evaluation fails with Groovy Exception"() {
        buildFile << """
    createTakk('do-stuff')
"""
        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'ProjectError'.")
                .assertHasCause("Could not find method createTakk() for arguments [do-stuff] on root project 'ProjectError")
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(2)
    }

    def "produces reasonable error message when buildFile evaluation fails on script compilation"() {
        buildFile << """
    // a comment
    import org.gradle.unknown.Unknown
    new Unknown()
"""

        when:
        fails()

        then:
        failure.assertHasDescription("Could not compile build file '${buildFile}'.")
                .assertThatCause(containsString("build file '$buildFile': 3: unable to resolve class org.gradle.unknown.Unknown"))
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when buildFile evaluation fails with exception"() {
        when:
        buildFile << """
    throw new RuntimeException("script failure")
    task test
"""
        then:
        fails('test')
        failure.assertHasDescription("A problem occurred evaluating root project 'ProjectError'.")
                .assertHasCause("script failure")
                .assertHasFileName("Build file '${buildFile.path}'")
                .assertHasLineNumber(2)
    }

    def "produces reasonable error message when nested buildFile evaluation fails"() {
        settingsFile << """
include 'child'
"""
        buildFile << """
    evaluationDependsOn 'child'
    task t
"""
        final childBuildFile = file("child/build.gradle")
        childBuildFile << """
    def broken = { ->
        throw new RuntimeException('failure')
    }
    broken()
"""

        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
                .assertHasCause("failure")
                .assertHasFileName("Build file '$childBuildFile'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message from a method inherited from a script containing only methods"() {
        settingsFile << """
include 'child'
"""
        buildFile << """
// Build script contains only methods
def broken() {
    throw new RuntimeException('failure')
}

def doSomething() {
    broken()
}
"""
        final childBuildFile = file("child/build.gradle")
        childBuildFile << """
    doSomething()
"""

        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':child'.")
                .assertHasCause("failure")
                .assertHasFileName("Build file '$buildFile'")
                .assertHasLineNumber(4)
    }
}
