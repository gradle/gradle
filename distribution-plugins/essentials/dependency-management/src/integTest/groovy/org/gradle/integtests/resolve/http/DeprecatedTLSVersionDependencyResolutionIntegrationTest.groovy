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
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.anyOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

class DeprecatedTLSVersionDependencyResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {

    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore = TestKeyStore.init(resources.dir)

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
        when:
        module.allowAll()
        then:
        executer.withStackTraceChecksDisabled()
        fails('listJars')
        and:
        failure.assertHasDescription("Execution failed for task ':listJars'")
        failure.assertHasCause("Could not GET '$server.uri")
        failure.assertThatCause(
            allOf(
                anyOf(
                    startsWith("The server does not support the client's requested TLS protocol versions:"),
                    startsWith("The server may not support the client's requested TLS protocol versions:") // Windows
                ),
                containsString("You may need to configure the client to allow other protocols to be used."),
                containsString("See: https://docs.gradle.org/")
            )
        )
    }

    // TLSv1 and TLSv1.1 are disabled by default on JDK16: https://bugs.openjdk.java.net/browse/JDK-8202343
    @Requires(TestPrecondition.JDK15_OR_EARLIER)
    def "able to resolve dependencies when the user manually specifies the supported TLS versions using `https.protocols`"() {
        given:
        keyStore.enableSslWithServerCert(mavenHttpRepo.server) {
            it.addExcludeProtocols("TLSv1.2", "TLSv1.3")
            it.setIncludeProtocols("TLSv1", "TLSv1.1")
            it.setExcludeCipherSuites()
        }
        keyStore.configureServerCert(executer)
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        and:
        writeBuildFile()
        when:
        module.allowAll()
        and:
        executer.withArgument("-Dhttps.protocols=TLSv1,TLSv1.1")
        then:
        succeeds('listJars')
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
            task listJars {
                doLast {
                    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
                }
            }
        """
    }
}
