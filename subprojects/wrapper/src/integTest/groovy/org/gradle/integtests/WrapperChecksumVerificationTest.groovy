/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle-private/issues/1537')
class WrapperChecksumVerificationTest extends AbstractWrapperIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.expect(server.get("/gradle-bin.zip").sendFile(distribution.binDistribution))
        server.start()
    }

    def "wrapper execution fails when using bad checksum"() {
        given:
        prepareWrapper(new URI("${server.uri}/gradle-bin.zip"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << 'distributionSha256Sum=bad'

        when:
        def failure = wrapperExecuter.withStackTraceChecksDisabled().runWithFailure()
        def f = new File(file("user-home/wrapper/dists/gradle-bin").listFiles()[0], "gradle-bin.zip")

        then:
        failure.error.contains("""
Verification of Gradle distribution failed!

Your Gradle distribution may have been tampered with.
Confirm that the 'distributionSha256Sum' property in your gradle-wrapper.properties file is correct and you are downloading the wrapper from a trusted source.

 Distribution Url: ${server.uri}/gradle-bin.zip
Download Location: $f.absolutePath
Expected checksum: 'bad'
  Actual checksum: '${HashUtil.sha256(distribution.binDistribution).asZeroPaddedHexString(64)}'
""".trim())
    }

    def "wrapper successfully verifies good checksum"() {
        given:
        prepareWrapper(new URI("${server.uri}/gradle-bin.zip"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << "distributionSha256Sum=${HashUtil.sha256(distribution.binDistribution).asZeroPaddedHexString(64)}"

        when:
        def success = wrapperExecuter.run()

        then:
        success.output.contains('BUILD SUCCESSFUL')
    }
}
