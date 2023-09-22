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
package org.gradle.integtests


import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperConcurrentDownloadTest extends AbstractWrapperIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.expect(server.head("/gradle-bin.zip"))
        server.expect(server.get("/gradle-bin.zip").sendFile(distribution.binDistribution))
        server.start()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2699")
    def "concurrent downloads do not stomp over each other"() {
        given:
        prepareWrapper(server.uri("gradle-bin.zip"))

        when:
        def results = [1..4].collect { wrapperExecuter.start() }*.waitForFinish()

        then:
        results.findAll { it.output.contains("Downloading") }.size() == 1
    }
}
