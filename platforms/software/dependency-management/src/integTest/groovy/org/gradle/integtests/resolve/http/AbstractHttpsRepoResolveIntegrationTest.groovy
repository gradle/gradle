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

package org.gradle.integtests.resolve.http

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.AuthScheme
import org.junit.Rule

import static org.gradle.util.Matchers.containsText
import static org.gradle.util.Matchers.matchesRegexp

abstract class AbstractHttpsRepoResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore

    abstract protected void setupRepo(boolean useAuth)

    abstract protected String getRepoType()

    def "resolve with server certificate and #authSchemeName authentication"() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        server.authenticationScheme = authScheme

        setupRepo(useAuth)
        setupBuildFile(repoType, useAuth)

        when:
        keyStore.configureServerCert(executer)
        succeeds "libs"

        then:
        file('libs').assertHasDescendants('my-module-1.0.jar')
        and:
        server.authenticationAttempts.asList() == authenticationAttempts

        where:
        useAuth | authScheme        | authSchemeName | authenticationAttempts
        false   | null              | 'no'           | ['None']
        true    | AuthScheme.BASIC  | 'basic'        | ['None', 'Basic']
        true    | AuthScheme.DIGEST | 'digest'       | ['None', 'Digest']
        true    | AuthScheme.NTLM   | 'ntlm'         | ['None', 'NTLM']
    }

    def "resolve with server and client certificate"() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerAndClientCerts(server)

        setupRepo()
        setupBuildFile(repoType)

        when:
        keyStore.configureServerAndClientCerts(executer)
        succeeds "libs"

        then:
        file('libs').assertHasDescendants('my-module-1.0.jar')
    }

    def "decent error message when client can't authenticate server"() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)

        setupBuildFile(repoType)

        when:
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        keyStore.configureIncorrectServerCert(executer)
        fails "libs"

        then:
        failure.assertHasCause("Could not GET '${server.uri}/repo1/my-group/my-module/1.0/")
        failure.assertThatCause(containsText("java.security.cert.CertPathValidatorException"))
    }

    def "build fails when server can't authenticate client"() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerAndBadClientCert(server)

        setupBuildFile(repoType)

        when:
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        keyStore.configureServerAndClientCerts(executer)
        fails "libs"

        then:
        failure.assertHasCause("Could not GET '${server.uri}/repo1/my-group/my-module/1.0/")
        // exact error might vary depending on JVM version and OS
        failure.assertThatCause(matchesRegexp("Got (socket|SSL handshake) exception during request. It might be caused by SSL misconfiguration"))
    }

    def "build fails when client has invalid ssl configuration and has underlying cause in output"() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)

        setupBuildFile(repoType)

        when:
        keyStore.configureServerAndClientCerts(executer)
        executer.withArgument("-Djavax.net.ssl.keyStore=Not-Existing-File").withArgument("--stacktrace")
        fails "libs"

        then:
        failure.assertHasCause("Could not resolve my-group:my-module:1.0")
        failure.assertHasErrorOutput("java.io.FileNotFoundException: Not-Existing-File")
        failure.assertHasErrorOutput("Could not initialize SSL context. Used properties")
        outputDoesNotContain("Trust store file ") // There should be no warning about trust store
    }

    def "build fails with a relevant message when client has invalid trust store configuration"() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)

        setupBuildFile(repoType)

        when:
        keyStore.configureServerAndClientCerts(executer)
        executer.withArgument("-Djavax.net.ssl.trustStore=Not-Existing-File")
        fails "libs"

        then:
        failure.assertHasCause("Could not resolve my-group:my-module:1.0")
        outputContains("Trust store file Not-Existing-File does not exist or is not readable. This may lead to SSL connection failures.")
        // depending on JVM version, the error might occur either during SSL context initialization or during request
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_18)) {
            failure.assertHasCause("Got SSL handshake exception during request. It might be caused by SSL misconfiguration")
        } else {
            failure.assertHasErrorOutput("Could not initialize SSL context. Used properties")
        }
    }

    private void setupBuildFile(String repoType, boolean withCredentials = false) {
        def credentials = """
credentials {
    username = 'user'
    password = 'secret'
}
"""

        buildFile << """
repositories {
    $repoType {
        url = '${server.uri}/repo1'
        ${withCredentials ? credentials : ''}
    }
}
configurations { compile }
dependencies {
    compile 'my-group:my-module:1.0'
}
task libs(type: Copy) {
    into 'libs'
    from configurations.compile
}
        """
    }
}

