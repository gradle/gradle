/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@DoesNotSupportNonAsciiPaths(reason = "Using non-Unicode default character set encoding")
class BuildScriptExecutionIntegrationSpec extends AbstractIntegrationSpec {

    def "build scripts must be encoded using utf-8"() {
        given:
        executer.withDefaultCharacterEncoding("ISO-8859-15")

        and:
        buildFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == "ISO-8859-15"
        // embed a euro character in the text - this is encoded differently in ISO-8859-12 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", "UTF-8")
        assert file('build.gradle').getText("ISO-8859-15") != file('build.gradle').getText("UTF-8")
        expect:
        succeeds 'check'
    }

    def "notices changes to build scripts that do not change the file length"() {
        buildFile.text = "task log { doLast { println 'counter: __' } }"
        int before = buildFile.length()

        expect:
        (10..40).each {
            buildFile.text = "task log { doLast { println 'counter: $it' } }"
            assert buildFile.length() == before

            succeeds('log')
            result.assertOutputContains("counter: $it")
        }
    }

}
