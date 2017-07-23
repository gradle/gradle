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
import org.gradle.test.fixtures.server.http.HttpServer
import spock.lang.Unroll

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

    @Unroll
    def "will used cached #source resource when run with --offline"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        file("init.gradle").createFile()
        buildFile << """
            task check {}
        """

        when:
        def scriptUri = "http://localhost:${server.port}/${scriptName}"
        file(sourceFile) << """
            apply from: '${scriptUri}'
        """

        then:
        args('-I', 'init.gradle')
        succeeds 'check'
        outputContains 'loaded external script'

        when:
        server.stop()

        then:
        args('-I', 'init.gradle')
        fails 'check'
        errorOutput.contains("Could not get resource '${scriptUri}'")

        when:
        scriptFile.setText("""throw new RuntimeException('NOT CACHED')""", "UTF-8")
        args('--offline', '-I', 'init.gradle')

        then:
        succeeds 'check'

        where:
        source        | sourceFile
        "buildscript" | "build.gradle"
        "settings"    | "settings.gradle"
        "initscript"  | "init.gradle"
    }

    def "will only request resource once for build invocation"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        settingsFile << """
            include ':projA'
            include ':projB'
        """

        and:
        [file('settings.gradle'), file('init.gradle'), file("projA/build.gradle"), file("projB/build.gradle")].each {
            it << "apply from: 'http://localhost:${server.port}/${scriptName}'"
        }

        when:
        args('-I', 'init.gradle')
        succeeds 'tasks'

        then:
        output.count('loaded external script') == 4
    }

    def "will refresh cached value on subsequent build invocation"() {
        given:
        String scriptName = "script-${System.currentTimeMillis()}.gradle"
        executer.withDefaultCharacterEncoding("ISO-8859-15")
        server.start()

        and:
        File scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script 1'""", "UTF-8")

        and:
        buildFile << "apply from: 'http://localhost:${server.port}/${scriptName}'"

        when:
        server.expectGet('/' + scriptName, scriptFile)

        then:
        succeeds 'tasks'
        output.contains('loaded external script 1')

        when:
        scriptFile.setText("""println 'loaded external script 2'""", "UTF-8")
        server.expectHead('/' + scriptName, scriptFile)
        server.expectGetMissing('/' + scriptName + '.sha1')
        server.expectGet('/' + scriptName, scriptFile)

        then:
        succeeds 'tasks'
        output.contains('loaded external script 2')

        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'tasks'
        output.contains('loaded external script 2')
    }
}
