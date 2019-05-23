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

class ExternalScriptErrorIntegrationTest extends AbstractIntegrationSpec {
    def externalScript

    def "setup"() {
        externalScript = file('other.gradle')
        settingsFile << "rootProject.name = 'project'"
        buildFile << """
    apply { from 'other.gradle' }
"""
    }

    def "produces reasonable error message when external script fails with Groovy exception"() {
        externalScript << '''

doStuff()
'''
        when:
        fails()

        then:
        failure.assertHasDescription('A problem occurred evaluating script.')
                .assertHasCause('Could not find method doStuff() for arguments [] on root project')
                .assertHasFileName("Script '${externalScript}'")
                .assertHasLineNumber(3)
    }

    def "produces reasonable error message when external script fails on compilation"() {
        externalScript << 'import org.gradle()'

        when:
        fails()

        then:
        failure.assertHasDescription("Could not compile script '$externalScript'.")
                .assertThatCause(containsString("script '${externalScript}': 1: unexpected token: ("))
                .assertHasFileName("Script '$externalScript'")
                .assertHasLineNumber(1)
    }

    def "reports missing script"() {
        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'project'.")
                .assertHasCause("Could not read script '${externalScript}' as it does not exist.")
                .assertHasFileName("Build file '${buildFile}'")
                .assertHasLineNumber(2)
    }

    def "produces reasonable error message when task execution fails"() {
        externalScript << '''
task doStuff {
    doLast {
        throw new RuntimeException('fail')
    }
}
'''
        when:
        fails 'doStuff'

        then:
        failure.assertHasDescription('Execution failed for task \':doStuff\'.')
                .assertHasCause('fail')
                .assertHasFileName("Script '${externalScript}'")
                .assertHasLineNumber(4)
    }
}

