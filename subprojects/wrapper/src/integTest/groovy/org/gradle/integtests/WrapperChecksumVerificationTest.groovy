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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.hash.Hashing
import org.gradle.process.internal.JvmOptions
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.wrapper.WrapperExecutor
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle-private/issues/1537')
@IgnoreIf({ GradleContextualExecuter.embedded }) // wrapperExecuter requires a real distribution
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
  Actual checksum: '${Hashing.sha256().hashFile(distribution.binDistribution).toZeroPaddedString(Hashing.sha256().hexDigits)}'
""".trim())
    }

    def "wrapper successfully verifies good checksum"() {
        given:
        prepareWrapper(new URI("${server.uri}/gradle-bin.zip"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << "distributionSha256Sum=${Hashing.sha256().hashFile(distribution.binDistribution).toZeroPaddedString(Hashing.sha256().hexDigits)}"

        when:
        def success = wrapperExecuter.run()
        then:
        success.output.contains('BUILD SUCCESSFUL')
    }

    def "wrapper requires checksum configuration if a checksum is present in gradle-wrapper.properties"() {
        given:
        prepareWrapper(new URI("${server.uri}/gradle-bin.zip"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << "distributionSha256Sum=${Hashing.sha256().hashFile(distribution.binDistribution).toZeroPaddedString(Hashing.sha256().hexDigits)}"

        when:
        def result = wrapperExecuter.withTasks("wrapper", "--gradle-version", "7.5").runWithFailure()

        then:
        result.assertHasErrorOutput("gradle-wrapper.properties contains distributionSha256Sum property, but the wrapper configuration does not have one. Specify one in the wrapped task configuration or with the --gradle-distribution-sha256-sum task option")
    }

    def "wrapper uses new checksum if it was provided as an option"() {
        given:
        prepareWrapper(new URI("${server.uri}/gradle-bin.zip"))

        and:
        file('gradle/wrapper/gradle-wrapper.properties') << "distributionSha256Sum=${Hashing.sha256().hashFile(distribution.binDistribution).toZeroPaddedString(Hashing.sha256().hexDigits)}"

        when: "Run wrapper to update with released distribution checksum and url"
        def releasedDistribution = IntegrationTestBuildContext.INSTANCE.distribution("7.5")
        def releasedDistributionUrl = releasedDistribution.binDistribution.toURI().toString()
        def releasedDistributionChecksum = Hashing.sha256().hashFile(releasedDistribution.binDistribution).toZeroPaddedString(Hashing.sha256().hexDigits)
        wrapperExecuter.withTasks("wrapper", "--gradle-distribution-url", releasedDistributionUrl, "--gradle-distribution-sha256-sum", releasedDistributionChecksum).run()

        then:
        file('gradle/wrapper/gradle-wrapper.properties').getProperties().get(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == releasedDistributionChecksum
    }

    def "wrapper preserves new checksum if it was provided in properties"() {
        given:
        def releasedDistribution = IntegrationTestBuildContext.INSTANCE.distribution("7.5")
        prepareWrapper(releasedDistribution.binDistribution.toURI())

        and:
        def underDevelopmentDistributionChecksum = Hashing.sha256().hashFile(distribution.binDistribution).toZeroPaddedString(Hashing.sha256().hexDigits)
        def underDevelopmentDistributionUrl = "${server.uri.toString().replace(":", "\\:")}/gradle-bin.zip"
        file('gradle/wrapper/gradle-wrapper.properties') << "distributionSha256Sum=$underDevelopmentDistributionChecksum\n"
        file('gradle/wrapper/gradle-wrapper.properties') << "distributionUrl=$underDevelopmentDistributionUrl"

        when:
        wrapperExecuter.withTasks("wrapper").run()

        then:
        file('gradle/wrapper/gradle-wrapper.properties').getProperties().get(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == underDevelopmentDistributionChecksum
    }
}
