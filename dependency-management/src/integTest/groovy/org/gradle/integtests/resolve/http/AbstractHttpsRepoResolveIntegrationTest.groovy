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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.AuthScheme
import org.junit.Rule

import static org.gradle.util.Matchers.containsText

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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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
    }

    private void setupBuildFile(String repoType, boolean withCredentials = false) {
        def credentials = """
credentials {
    username 'user'
    password 'secret'
}
"""

        buildFile << """
repositories {
    $repoType {
        url '${server.uri}/repo1'
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

