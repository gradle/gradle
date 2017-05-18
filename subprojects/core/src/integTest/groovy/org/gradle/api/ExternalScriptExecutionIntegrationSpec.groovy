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

import org.apache.commons.lang3.StringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer

class ExternalScriptExecutionIntegrationSpec extends AbstractIntegrationSpec {
    @org.junit.Rule
    public final HttpServer server = new HttpServer()

    def "uses encoding specified by http server"() {
        given:
        executer.withDefaultCharacterEncoding("UTF-8")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == "UTF-8"
        // embed a euro character in the text - this is encoded differently in ISO-8859-15 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", "ISO-8859-15")
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText("UTF-8")
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain; charset=ISO-8859-15")

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/script.gradle'"

        expect:
        succeeds 'check'
    }

    def "assumes utf-8 encoding when none specified by http server"() {
        given:
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""
task check {
    doLast {
        assert java.nio.charset.Charset.defaultCharset().name() == "ISO-8859-15"
        // embed a euro character in the text - this is encoded differently in ISO-8859-15 and UTF-8
        assert '\u20AC'.charAt(0) == 0x20AC
    }
}
""", "UTF-8")
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText("UTF-8")
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain")

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/script.gradle'"

        expect:
        succeeds 'check'
    }

    def "will cache when offline"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""task check { }""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/${scriptName}'"

        expect:
        succeeds 'check'

        and:
        when:
        server.stop()
        scriptFile.setText("""task check { throw new GradleException() }""", "UTF-8")

        then:
        fails 'check'
        errorOutput.contains("Could not get resource")

        and:
        when:
        args("--offline")

        then:
        succeeds 'check'
    }

    def "will cache settings.gradle when offline"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'from script'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        settingsFile << "apply from: 'http://localhost:${server.port}/${scriptName}'"
        buildFile << "task check { println 'from build.gradle' }"

        expect:
        succeeds 'check'
        output.contains("from script")

        and:
        when:
        server.stop()

        then:
        args("--offline")
        succeeds 'check'
        output.contains("from script")
    }

    def "will cache initscript when offline"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'from init'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        File initScript = file("init-script.gradle")
        initScript << "apply from: 'http://localhost:${server.port}/${scriptName}'"
        buildFile << "task check { println 'from build.gradle' }"

        expect:
        args("-I", initScript.absolutePath)
        succeeds 'check'
        output.contains("from init")

        and:
        when:
        server.stop()

        then:
        args("--offline", "-I", initScript.absolutePath)
        succeeds 'check'
        output.contains("from init")
    }

    def "will reuse request"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'from applied'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        file("projA/build.gradle") << "apply from: 'http://localhost:${server.port}/${scriptName}'"
        file("projB/build.gradle") << "apply from: 'http://localhost:${server.port}/${scriptName}'"

        and:
        settingsFile << "include ':projA'\n"
        settingsFile << "include ':projB'\n"

        expect:
        succeeds 'tasks'
        2 == StringUtils.countMatches(output, "from applied")

        and:
        when:
        server.stop()

        then:
        args("--offline")
        succeeds 'tasks'
        2 == StringUtils.countMatches(output, "from applied")
    }

    def "cached values will be overwritten with second request"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        File scriptFile = file("script.gradle")
        scriptFile.setText("""println 'in script 1'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)
        server.expectHead('/' + scriptName, scriptFile)
        server.expectGetMissing('/' + scriptName + '.sha1')
        server.expectGet('/' + scriptName, scriptFile)

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/${scriptName}'"

        expect:
        succeeds 'tasks'
        output.contains('in script 1')

        and:
        when:
        scriptFile.setText("""println 'in script 2'""", "UTF-8")

        then:
        succeeds 'tasks'
        output.contains('in script 2')

        and:
        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'tasks'
        output.contains('in script 2')
    }
}
