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
import org.gradle.invocation.DefaultGradle

import static org.hamcrest.CoreMatchers.containsString

public class InitScriptErrorIntegrationTest extends AbstractIntegrationSpec {
    def initScript

    def "setup"() {
        initScript = file('init.gradle')
        executer.usingInitScript(initScript)
    }

    def "produces reasonable error message when init script evaluation fails with GroovyException"() {
        initScript << """
    createTakk('do-stuff')
"""
        when:
        fails()

        then:
        failure.assertHasDescription("A problem occurred evaluating initialization script.")
                .assertHasCause("Could not find method createTakk() for arguments [do-stuff] on build of type ${DefaultGradle.name}.")
                .assertHasFileName("Initialization script '$initScript'")
                .assertHasLineNumber(2)
    }

    def "produces reasonable error message when init script compilation fails"() {
        initScript << """
    // a comment
    import org.gradle.unknown.Unknown
    new Unknown()
"""
        when:
        fails()

        then:
        failure.assertHasDescription("Could not compile initialization script '$initScript'.")
                .assertThatCause(containsString("initialization script '$initScript': 3: unable to resolve class org.gradle.unknown.Unknown"))
                .assertHasFileName("Initialization script '$initScript'")
                .assertHasLineNumber(3)
    }
}
