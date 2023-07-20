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

import com.gradle.enterprise.testing.annotations.LocalOnly
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.wrapper.WrapperExecutor
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Shared

import static org.gradle.internal.hash.Hashing.sha256

// wrapperExecuter requires a real distribution
@IgnoreIf({ GradleContextualExecuter.embedded })
@LocalOnly(because = "https://github.com/gradle/gradle-private/issues/3799")
class WrapperChecksumVerificationTest extends AbstractWrapperIntegrationSpec {

    private static final String WRAPPER_PROPERTIES_PATH = 'gradle/wrapper/gradle-wrapper.properties'

    @Shared String cachedDistributionHash;

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def configureServer(boolean expectHead) {
        if (expectHead) {
            server.expect(server.head("/gradle-bin.zip"))
        }
        server.expect(server.get("/gradle-bin.zip").sendFile(distribution.binDistribution))
        server.start()
    }

    def "wrapper execution fails when using bad checksum"() {
        given:
        configureServer(true)
        prepareWrapper(new URI(gradleBin))

        and:
        file(WRAPPER_PROPERTIES_PATH) << 'distributionSha256Sum=bad'

        when:
        def failure = wrapperExecuter.withStackTraceChecksDisabled().runWithFailure()
        def f = new File(file("user-home/wrapper/dists/gradle-bin").listFiles()[0], "gradle-bin.zip")

        then:
        failure.error.contains("""
Verification of Gradle distribution failed!

Your Gradle distribution may have been tampered with.
Confirm that the 'distributionSha256Sum' property in your gradle-wrapper.properties file is correct and you are downloading the wrapper from a trusted source.

 Distribution Url: $gradleBin
Download Location: $f.absolutePath
Expected checksum: 'bad'
  Actual checksum: '$distributionHash'
""".trim())
    }

    private String getGradleBin() {
        "${server.uri}/gradle-bin.zip"
    }

    String getDistributionHash() {
        if(cachedDistributionHash == null){
            cachedDistributionHash = getDistributionHash(distribution)
        }
        cachedDistributionHash
    }

    def "wrapper successfully verifies good checksum"() {
        given:
        configureServer(true)
        prepareWrapper(new URI(gradleBin))

        and:
        writeValidDistributionHash()

        when:
        def success = wrapperExecuter.run()
        then:
        success.output.contains('BUILD SUCCESSFUL')
    }

    def "wrapper requires checksum configuration if a checksum is present in gradle-wrapper.properties"() {
        given:
        configureServer(true)
        prepareWrapper(new URI(gradleBin))

        and:
        writeValidDistributionHash()

        when:
        def result = wrapperExecuter.withTasks("wrapper", "--gradle-version", "7.5").runWithFailure()

        then:
        result.assertHasErrorOutput("gradle-wrapper.properties contains distributionSha256Sum property, but the wrapper configuration does not have one. " +
            "Specify one in the wrapped task configuration or with the --gradle-distribution-sha256-sum task option")
    }

    private writeValidDistributionHash() {
        file(WRAPPER_PROPERTIES_PATH) << "distributionSha256Sum=${distributionHash}"
    }

    def "wrapper uses new checksum if it was provided as an option"() {
        given:
        configureServer(true)
        prepareWrapper(new URI(gradleBin))

        and:
        writeValidDistributionHash()

        when: "Run wrapper to update with released distribution checksum and url"
        def releasedDistribution = IntegrationTestBuildContext.INSTANCE.distribution("7.5")
        def releasedDistributionUrl = releasedDistribution.binDistribution.toURI().toString()
        def releasedDistributionChecksum = getDistributionHash(releasedDistribution)
        wrapperExecuter.withTasks("wrapper", "--gradle-distribution-url", releasedDistributionUrl, "--gradle-distribution-sha256-sum", releasedDistributionChecksum).run()

        then:
        file(WRAPPER_PROPERTIES_PATH).getProperties().get(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == releasedDistributionChecksum
    }

    static String getDistributionHash(GradleDistribution distribution) {
        sha256().hashFile(distribution.binDistribution).toZeroPaddedString(sha256().hexDigits)
    }

    def "wrapper preserves new checksum if it was provided in properties"() {
        given:
        configureServer(false)
        def releasedDistribution = IntegrationTestBuildContext.INSTANCE.distribution("7.5")
        prepareWrapper(releasedDistribution.binDistribution.toURI())

        and:
        def underDevelopmentDistributionChecksum = distributionHash
        def underDevelopmentDistributionUrl = "${server.uri.toString().replace(":", "\\:")}/gradle-bin.zip"
        file(WRAPPER_PROPERTIES_PATH) << "distributionSha256Sum=$underDevelopmentDistributionChecksum\n"
        file(WRAPPER_PROPERTIES_PATH) << "distributionUrl=$underDevelopmentDistributionUrl"

        when:
        wrapperExecuter.withTasks("wrapper").run()

        then:
        file(WRAPPER_PROPERTIES_PATH).getProperties().get(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == underDevelopmentDistributionChecksum
    }
}
