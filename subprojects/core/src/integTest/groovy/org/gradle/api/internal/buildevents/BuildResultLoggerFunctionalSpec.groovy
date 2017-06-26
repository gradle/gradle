/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.buildevents

import org.fusesource.jansi.Ansi
import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec

class BuildResultLoggerFunctionalSpec extends AbstractConsoleFunctionalSpec {

    def setup() {
        executer.withStackTraceChecksDisabled()
    }

    def "Failure status is logged even in --quiet"() {
        given:
        buildFile << "task fail { doFirst { assert false } }"

        when:
        executer.withQuietLogging()
        fails('fail')

        then:
        result.output.contains("BUILD FAILED")
    }

    def "Failure message is logged bold and red"() {
        given:
        buildFile << "task fail { doFirst { assert false } }"

        when:
        fails('fail')

        then:
        result.output.contains(styled("BUILD FAILED", Ansi.Color.RED, Ansi.Attribute.INTENSITY_BOLD))
    }

    def "Success message is logged bold and green in the rich console"() {
        given:
        buildFile << "task success"

        when:
        succeeds('success')

        then:
        result.output.contains(styled('BUILD SUCCESSFUL', Ansi.Color.GREEN, Ansi.Attribute.INTENSITY_BOLD))
    }
}
