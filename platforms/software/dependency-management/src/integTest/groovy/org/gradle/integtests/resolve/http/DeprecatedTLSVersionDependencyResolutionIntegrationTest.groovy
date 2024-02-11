/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.integtests.fixtures.resolve.ResolveFailureTestFixture
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.junit.Rule

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

class DeprecatedTLSVersionDependencyResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore = TestKeyStore.init(resources.dir)
    ResolveFailureTestFixture failedResolve = new ResolveFailureTestFixture(buildFile)

    def "unable to resolve dependencies when server only supports deprecated TLS versions"() {
        given:
        keyStore.enableSslWithServerCert(mavenHttpRepo.server) {
            it.addExcludeProtocols("TLSv1.2", "TLSv1.3")
            it.setIncludeProtocols("TLSv1", "TLSv1.1")
        }
        keyStore.configureServerCert(executer)
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        writeBuildFile()
        failedResolve.prepare()

        when:
        module.allowAll()

        then:
        executer.withStackTraceChecksDisabled()
        fails('checkDeps')

        and:
        failedResolve.assertFailurePresent(failure)
        failure.assertHasCause("Could not GET '$server.uri")
        failure.assertThatCause(
            allOf(
                anyOf(
                    startsWith("The server does not support the client's requested TLS protocol versions:"),
                    startsWith("The server may not support the client's requested TLS protocol versions:") // Windows
                ),
                containsString("You may need to configure the client to allow other protocols to be used."),
                containsString(documentationRegistry.getDocumentationRecommendationFor("on this", "build_environment", "sec:gradle_system_properties"))
            )
        )
    }

    def "build fails when user specifies `https.protocols` that are not supported by the server"() {
        given:
        keyStore.enableSslWithServerCert(mavenHttpRepo.server) {
            it.addExcludeProtocols("TLSv1.3")
            it.setIncludeProtocols("TLSv1.2")
        }
        keyStore.configureServerCert(executer)
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        and:
        writeBuildFile()
        failedResolve.prepare()

        when:
        module.allowAll()

        and:
        executer.withArgument("-Dhttps.protocols=TLSv1.3")

        then:
        fails('checkDeps')
        failedResolve.assertFailurePresent(failure)
    }

    def writeBuildFile() {
        buildFile << """
            repositories {
                maven {
                    url "${mavenHttpRepo.uri}"
                }
            }
            configurations { compile }
            dependencies {
                compile 'group:projectA:1.2'
            }
        """
    }
}
