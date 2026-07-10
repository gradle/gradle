/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.groovy.scripts

import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.Severity
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.hamcrest.CoreMatchers

class StatementLabelsIntegrationTest extends AbstractIntegrationSpec {
    def "use of statement label in build script is reported"() {
        given:
        enableProblemsApiCheck()
        buildFile << """
version: '1.0'
        """

        when:
        fails("tasks")

        then:
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasLineNumber(2)
        failureDescriptionContains("Could not compile build file '${buildFile}'.")
        failure.assertThatCause(CoreMatchers.containsString("build file '${buildFile}': 2: Statement labels may not be used in build scripts."))
        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'compilation:groovy-dsl:compilation-failed'
            definition.id.displayName == 'Groovy DSL script compilation problem'
            contextualLabel == "Could not compile build file '${buildFile}'."
            oneLocation(LineInFileLocation).path == buildFile.absolutePath
        }

        when:
        // try again to make sure that warning sticks if build script is cached
        fails("tasks")

        then:
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasLineNumber(2)
        failureDescriptionContains("Could not compile build file '${buildFile}'.")
        failure.assertThatCause(CoreMatchers.containsString("build file '${buildFile}': 2: Statement labels may not be used in build scripts."))
        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'compilation:groovy-dsl:compilation-failed'
            definition.id.displayName == 'Groovy DSL script compilation problem'
            contextualLabel == "Could not compile build file '${buildFile}'."
            oneLocation(LineInFileLocation).path == buildFile.absolutePath
        }
    }

    def "all usages of statement labels are reported"() {
        given:
        enableProblemsApiCheck()
        buildFile << """
version: '1.0'
group = "foo"
description: "bar"
        """

        when:
        fails("tasks")

        then:
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasLineNumber(2)
        failureDescriptionContains("Could not compile build file '${buildFile}'.")
        failure.assertThatCause(CoreMatchers.containsString("build file '${buildFile}': 2: Statement labels may not be used in build scripts."))
        failure.assertThatCause(CoreMatchers.containsString("build file '${buildFile}': 4: Statement labels may not be used in build scripts."))
        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'compilation:groovy-dsl:compilation-failed'
            definition.id.displayName == 'Groovy DSL script compilation problem'
            contextualLabel == "Could not compile build file '${buildFile}'."
            oneLocation(LineInFileLocation).path == buildFile.absolutePath
        }
    }

    def "nested use of statement label in build script is reported"() {
        given:
        enableProblemsApiCheck()
        buildFile << """
def foo() {
    1.times {
      for (i in 1..1) {
        another: "label"
      }
    }
}
        """

        when:
        fails("tasks")

        then:
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertHasLineNumber(5)
        failureDescriptionContains("Could not compile build file '${buildFile}'.")
        failure.assertThatCause(CoreMatchers.containsString("build file '${buildFile}': 5: Statement labels may not be used in build scripts."))
        verifyAll(receivedProblem(0)) {
            severity == Severity.ERROR
            fqid == 'compilation:groovy-dsl:compilation-failed'
            definition.id.displayName == 'Groovy DSL script compilation problem'
            contextualLabel == "Could not compile build file '${buildFile}'."
            oneLocation(LineInFileLocation).path == buildFile.absolutePath
        }
    }

    def "use of statement label in class inside build script is allowed"() {
        buildFile << """
class Foo {
  def bar() {
    mylabel:
    def x = 1
  }
}
        """

        expect:
        succeeds("help")
    }
}
