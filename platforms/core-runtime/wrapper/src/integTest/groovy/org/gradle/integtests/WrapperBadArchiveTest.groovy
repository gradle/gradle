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

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.integtests.WrapperChecksumVerificationTest.getDistributionHash

@Issue('https://github.com/gradle/gradle-private/issues/1537')
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperBadArchiveTest extends AbstractWrapperIntegrationSpec {

    private static final String GRADLE_BIN_ZIP = "/gradle-bin.zip"
    private static final String GRADLE_BIN_HASH = "/gradle-bin.zip.sha256"
    private static final String BAD_ARCHIVE_CONTENT = "bad archive content"

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    TestFile badArchive = file("bad-archive.zip") << BAD_ARCHIVE_CONTENT

    def "wrapper gets bad archive on 1 attempt"() {
        given:
        server.expect(server.head(GRADLE_BIN_ZIP))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.expect(server.get(GRADLE_BIN_HASH).missing())
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(distribution.binDistribution))
        server.start()

        prepareWrapperWithGradleBin()

        when:
        def success = wrapperExecuter.run()

        then:
        assertSucces(success)
    }

    private prepareWrapperWithGradleBin() {
        prepareWrapper(new URI("$server.uri$GRADLE_BIN_ZIP"))
    }

    def "wrapper gets bad archive on 2 attempts"() {
        given:
        server.expect(server.head(GRADLE_BIN_ZIP))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.expect(server.get(GRADLE_BIN_HASH).missing())
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(distribution.binDistribution))
        server.start()

        prepareWrapperWithGradleBin()

        when:
        def success = wrapperExecuter.run()

        then:
        assertSucces(success)
    }

    private assertSucces(ExecutionResult success) {
        success.output.contains('BUILD SUCCESSFUL')
    }

    def "wrapper gets bad archive on 3 attempts"() {
        given:
        server.expect(server.head(GRADLE_BIN_ZIP))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.expect(server.get(GRADLE_BIN_HASH).missing())
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.start()

        prepareWrapperWithGradleBin()

        when:
        def failure = wrapperExecuter
            .withStackTraceChecksDisabled()
            .runWithFailure()

        then:
        failure.error.matches(/[\S\s]*Downloaded distribution file .* is no valid zip file\.[\S\s]*/)
    }

    def "wrapper gets bad archive on 1 attempt and good hash"() {
        given:
        server.expect(server.head(GRADLE_BIN_ZIP))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(badArchive))
        server.expect(server.get(GRADLE_BIN_HASH).send(getDistributionHash(distribution)))
        server.expect(server.get(GRADLE_BIN_ZIP).sendFile(distribution.binDistribution))
        server.start()

        prepareWrapperWithGradleBin()

        when:
        def success = wrapperExecuter.run()

        then:
        assertSucces(success)
    }

}
