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

import org.gradle.api.resources.TextResourceFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.internal.deprecation.Documentation
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import spock.lang.Issue

import static org.junit.Assert.fail

class HttpScriptPluginIntegrationSpec extends AbstractIntegrationSpec {
    @org.junit.Rule
    HttpServer server = new HttpServer()
    @org.junit.Rule
    TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name = 'project'"
        server.expectUserAgent(UserAgentMatcher.matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        server.start()
        executer.requireOwnGradleUserHomeDir()
    }

    private void applyTrustStore() {
        def keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        keyStore.configureServerCert(executer)
    }

    def "can apply script via http"() {
        when:
        def script = file('external.gradle')
        server.expectGet('/external.gradle', script)

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle" == buildscript.sourceURI as String
"""

        buildFile << """
            apply from: '$server.uri/external.gradle'
            defaultTasks 'doStuff'
"""

        then:
        succeeds()
    }

    def "emits useful warning when applying script via http"() {
        when:
        server.useHostname()
        def script = file('external.gradle')
        server.beforeHandle {
            fail("No requests were expected.")
        }

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle" == buildscript.sourceURI as String
"""

        buildFile << """
            apply from: '$server.uri/external.gradle'
            defaultTasks 'doStuff'
"""

        then:
        ExecutionFailure failure = fails(":help")
        failure.assertHasCause("Applying script plugins from insecure URIs, without explicit opt-in, is unsupported. " +
            "The provided URI '${server.uri("/external.gradle")}' uses an insecure protocol (HTTP). " +
            "Use '${GUtil.toSecureUrl(server.uri("/external.gradle"))}' instead or try 'apply from: resources.text.fromInsecureUri(\"${server.uri("/external.gradle")}\")' to fix this. " +
            Documentation.dslReference(TextResourceFactory.class, "fromInsecureUri(java.lang.Object)").consultDocumentationMessage()
        )
    }

    def "does not complain when applying script plugin via http using text resource"() {
        when:
        server.useHostname()
        def script = file('external.gradle')
        server.expectGet('/external.gradle', script)

        script << """
            task doStuff
        """

        buildFile << """
            apply from: resources.text.fromInsecureUri("$server.uri/external.gradle")
            defaultTasks 'doStuff'
        """

        then:
        succeeds()
    }

    def "can apply script via https"() {
        applyTrustStore()

        when:
        def script = file('external.gradle')
        server.expectGet('/external.gradle', script)

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle" == buildscript.sourceURI as String
"""

        buildFile << """
            apply from: '$server.uri/external.gradle'
            defaultTasks 'doStuff'
"""

        then:
        succeeds()
    }

    @Issue("https://github.com/gradle/gradle/issues/2891")
    def "can apply script with URI containing a query string"() {
        when:
        def queryString = 'p=foo;a=blob_plain;f=bar;hb=foo/bar/foo'
        def script = file('external.gradle')
        server.expectGetWithQueryString('/external.gradle', queryString, script)

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle?$queryString" == buildscript.sourceURI as String
"""

        buildFile << """
            apply from: '$server.uri/external.gradle?$queryString'
            defaultTasks 'doStuff'
"""

        then:
        succeeds()
    }

    def "scripts with same URI path but different query strings are treated as separate things"() {
        when:
        def first = file('first.gradle') << """
            task first
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle?first" == buildscript.sourceURI as String
        """
        def second = file('second.gradle') << """
            task second
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle?second" == buildscript.sourceURI as String
        """
        server.expectGetWithQueryString('/external.gradle', "first", first)
        server.expectGetWithQueryString('/external.gradle', "second", second)

        buildFile << """
            apply from: '$server.uri/external.gradle?first'
            apply from: '$server.uri/external.gradle?second'
            defaultTasks 'first', 'second'
"""

        then:
        succeeds()
    }

    @ToBeFixedForConfigurationCache(because = "remote scripts skipped")
    def "does not cache URIs with query parts"() {
        when:
        def queryString = 'p=foo;a=blob_plain;f=bar;hb=foo/bar/foo'
        def script = file('external.gradle')
        server.expectGetWithQueryString('/external.gradle', queryString, script)

        script << """
            task doStuff
            assert buildscript.sourceFile == null
            assert "${server.uri}/external.gradle?$queryString" == buildscript.sourceURI as String
"""

        buildFile << """
            apply from: '$server.uri/external.gradle?$queryString'
            defaultTasks 'doStuff'
"""

        then:
        succeeds()

        when:
        server.expectGetWithQueryString('/external.gradle', queryString, script)
        then:
        succeeds()

        when:
        server.stop()
        then:
        fails()
    }

    def "reasonable error message while --offline when applying a script with a query part"() {
        def url = "$server.uri/external.gradle?query"
        buildFile << """
            apply from: '$url'
            defaultTasks 'doStuff'
        """
        server.stop()
        expect:
        fails("--offline")
        failure.assertHasCause("Could not read script '$url'")
    }

    def "uses encoding specified by http server"() {
        given:
        executer.withDefaultCharacterEncoding("UTF-8")

        and:
        def scriptFile = file("script-encoding.gradle")
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
        buildFile << "apply from: '${server.uri}/script.gradle'"

        expect:
        succeeds 'check'
    }

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
""", "UTF-8")
        assert scriptFile.getText("ISO-8859-15") != scriptFile.getText("UTF-8")
        server.expectGet('/script.gradle', scriptFile).contentType("text/plain")

        and:
        buildFile << "apply from: '${server.uri}/script.gradle'"

        expect:
        succeeds 'check'
    }

    def "will not download cached #source resource when run with --offline"() {
        given:
        def scriptName = "script-offline.gradle"
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        file("init.gradle").createFile()
        buildFile << """
            task check {}
        """

        when:
        def scriptUri = "${server.uri}/${scriptName}"
        file(sourceFile) << """
            apply from: '${scriptUri}'
        """

        then:
        args('-I', 'init.gradle')
        succeeds 'check'
        outputContains 'loaded external script'

        when:
        server.resetExpectations()
        args('--offline', '-I', 'init.gradle')

        then:
        succeeds 'check'

        where:
        source        | sourceFile
        "buildscript" | "build.gradle"
        "settings"    | "settings.gradle"
        "initscript"  | "init.gradle"
    }

    @ToBeFixedForConfigurationCache(because = "remote scripts skipped")
    def "can recover from failure to download cached #source resource by running with --offline"() {
        given:
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script'""", "UTF-8")
        server.expectGet('/' + scriptName, scriptFile)

        and:
        file("init.gradle").createFile()
        buildFile << """
            task check {}
        """

        when:
        def scriptUri = "${server.uri}/${scriptName}"
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
        failure.assertHasCause("Could not get resource '${scriptUri}'")

        when:
        args('--offline', '-I', 'init.gradle')

        then:
        succeeds 'check'

        where:
        source        | sourceFile        | scriptName
        "buildscript" | "build.gradle"    | "build-script-plugin.gradle"
        "settings"    | "settings.gradle" | "settings-script-plugin.gradle"
        "initscript"  | "init.gradle"     | "init-script-plugin.gradle"
    }

    @ToBeFixedForConfigurationCache(because = "test expects script evaluation")
    def "will only request resource once for build invocation"() {
        given:
        def scriptName = "script-once.gradle"
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script'""", "UTF-8")

        and:
        settingsFile << """
            include ':projA'
            include ':projB'
        """

        and:
        [file('settings.gradle'), file('init.gradle'), file("projA/build.gradle"), file("projB/build.gradle")].each {
            it << "apply from: '${server.uri}/${scriptName}'"
        }

        when:
        server.expectGet('/' + scriptName, scriptFile)
        args('-I', 'init.gradle')

        then:
        succeeds 'help'
        output.count('loaded external script') == 4

        when:
        server.resetExpectations()
        server.expectHead('/' + scriptName, scriptFile)
        args('-I', 'init.gradle')

        then:
        succeeds 'help'
        output.count('loaded external script') == 4
    }

    def "will refresh cached value on subsequent build invocation"() {
        given:
        def scriptName = "script-cached.gradle"
        def scriptFile = file("script.gradle")
        scriptFile.setText("""println 'loaded external script 1'""", "UTF-8")
        scriptFile.makeOlder()

        and:
        buildFile << "apply from: '${server.uri}/${scriptName}'"

        when:
        server.expectGet('/' + scriptName, scriptFile)

        then:
        succeeds 'help'
        output.contains('loaded external script 1')

        when:
        scriptFile.setText("""println 'loaded external script 2'""", "UTF-8")
        server.expectHead('/' + scriptName, scriptFile)
        server.expectGet('/' + scriptName, scriptFile)

        then:
        succeeds 'help'
        output.contains('loaded external script 2')

        when:
        server.stop()
        args("--offline")

        then:
        succeeds 'help'
        output.contains('loaded external script 2')
    }

    def "reports and recovers from missing remote script"() {
        String scriptName = "script-missing.gradle"
        String scriptUrl = "${server.uri}/${scriptName}"
        def scriptFile = file("script.gradle") << """
            println 'loaded remote script'
        """

        buildFile << """
            apply from: '${scriptUrl}'
        """

        when:
        server.expectGetMissing("/" + scriptName)

        then:
        fails()

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'project'.")
            .assertHasCause("Could not read '${scriptUrl}' as it does not exist.")
            .assertHasFileName("Build file '${buildFile}'")
            .assertHasLineNumber(2)

        when:
        server.resetExpectations()
        server.expectGet("/" + scriptName, scriptFile)

        then:
        succeeds()

        and:
        outputContains("loaded remote script")
    }
}
