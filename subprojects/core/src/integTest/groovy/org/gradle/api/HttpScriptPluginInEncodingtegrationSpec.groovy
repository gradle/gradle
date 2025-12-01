/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import sun.nio.cs.UTF_8

@DoesNotSupportNonAsciiPaths(reason = "Uses non-Unicode default charset")
class HttpScriptPluginInEncodingtegrationSpec extends AbstractHttpScriptPluginIntegrationSpec {

    def "uses encoding specified by http server"() {
        given:
        executer.withDefaultCharacterEncoding(UTF_8.toString())

        and:
        def scriptFile = file("script-encoding.gradle")
        scriptFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == UTF_8.toString()
        // embed a euro character in the text - this is encoded differently in ISO-8859-15 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", "ISO-8859-15")
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText(UTF_8.toString())
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain; charset=ISO-8859-15")

        and:
        buildFile << "apply from: '${server.uri}/script.gradle'"

        expect:
        succeeds 'check'
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "requires explicit encoding")
    def "assumes utf-8 encoding when none specified by http server"() {
        given:
        applyTrustStore()
        executer.withDefaultCharacterEncoding("ISO-8859-15")

        and:
        def scriptFile = file("script-assumed-encoding.gradle")
        scriptFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == "ISO-8859-15"
        // embed a euro character in the text - this is encoded differently in ISO-8859-15 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", UTF_8.toString())
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText(UTF_8.toString())
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain")

        and:
        buildFile << "apply from: '${server.uri}/script.gradle'"

        expect:
        succeeds 'check'
    }
}
