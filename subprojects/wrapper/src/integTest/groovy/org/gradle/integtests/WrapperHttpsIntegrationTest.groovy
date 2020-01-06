/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.wrapper.Download
import org.junit.Rule

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

class WrapperHttpsIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule BlockingHttpsServer server = new BlockingHttpsServer()

    def setup() {
        TestKeyStore keyStore = TestKeyStore.init(resources.dir)
        // We need to set the SSL properties as arguments here even for non-embedded test mode
        // because we want them to be set on the wrapper client JVM, not the daemon one
        wrapperExecuter.withArgument("-Djavax.net.ssl.trustStore=$keyStore.keyStore.path")
        wrapperExecuter.withArgument("-Djavax.net.ssl.trustStorePassword=$keyStore.keyStorePassword")
        server.configure(keyStore)
        server.withBasicAuthentication("jdoe", "changeit")
        server.start()

        file("build.gradle") << """
    task hello {
        doLast {
            println 'hello'
        }
    }

    task echoProperty {
        doLast {
            println "fooD=" + project.properties["fooD"]
        }
    }
"""
    }

    private prepareWrapper(String baseUrl) {
        prepareWrapper(new URI("${baseUrl}/gradlew/dist"))
    }

    def "does not warn about using basic authentication over secure connection"() {
        given:
        prepareWrapper("https://jdoe:changeit@localhost:${server.port}")
        server.expect(server.get("/gradlew/dist")
                .expectUserAgent(matchesNameAndVersion("gradlew", Download.UNKNOWN_VERSION))
                .sendFile(distribution.binDistribution))


        when:
        result = wrapperExecuter.withTasks('hello').run()

        then:
        outputDoesNotContain('WARNING Using HTTP Basic Authentication over an insecure connection to download the Gradle distribution. Please consider using HTTPS.')
    }
}
