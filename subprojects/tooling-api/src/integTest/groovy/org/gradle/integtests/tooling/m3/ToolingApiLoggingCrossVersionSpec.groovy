/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.tooling.m3

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ToolingApiLoggingCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.isEmbedded = false
    }

    def "client receives same standard output and standard error as if running from the command-line"() {
        toolingApi.verboseLogging = false

        file("build.gradle") << """
System.err.println "System.err \u03b1\u03b2"

println "System.out \u03b1\u03b2"

project.logger.error("error logging \u03b1\u03b2");
project.logger.warn("warn logging");
project.logger.lifecycle("lifecycle logging \u03b1\u03b2");
project.logger.quiet("quiet logging");
project.logger.info ("info logging");
project.logger.debug("debug logging");
"""
        when:
        def commandLineResult = targetDist.executer(temporaryFolder).run();

        and:
        def op = withBuild()

        then:
        def out = op.standardOutput
        def err = op.standardError
        normaliseOutput(out) == normaliseOutput(commandLineResult.output)
        err == commandLineResult.error

        and:
        err.count("System.err \u03b1\u03b2") == 1
        err.count("error logging \u03b1\u03b2") == 1

        and:
        out.count("lifecycle logging \u03b1\u03b2") == 1
        out.count("warn logging") == 1
        out.count("quiet logging") == 1
        out.count("info") == 0
        out.count("debug") == 0
    }

    def "logging is live"() {
        def marker = file("marker.txt")

        file("build.gradle") << """
task log << {
    println "waiting"
    def marker = file('${marker.toURI()}')
    long timeout = System.currentTimeMillis() + 10000
    while (!marker.file && System.currentTimeMillis() < timeout) { Thread.sleep(200) }
    if (!marker.file) { throw new RuntimeException("Timeout waiting for marker file") }
    println "finished"
}
"""

        when:
        def resultHandler = new TestResultHandler()
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.forTasks("log")
            build.run(resultHandler)
            ConcurrentTestUtil.poll(10) { output.toString().contains("waiting") }
            marker.text = 'go!'
            resultHandler.finished()
        }

        then:
        output.toString().contains("waiting")
        output.toString().contains("finished")
    }

    String normaliseOutput(String output) {
        return output.replaceFirst("Total time: .+ secs", "Total time: 0 secs")
    }

    class TestResultHandler implements ResultHandler<Object> {
        final latch = new CountDownLatch(1)
        def failure

        void onComplete(Object result) {
            latch.countDown()
        }

        void onFailure(GradleConnectionException failure) {
            this.failure = failure
            latch.countDown()
        }

        def finished() {
            latch.await(10, TimeUnit.SECONDS)
            if (failure != null) {
                throw failure
            }
        }
    }

    class TestOutputStream extends OutputStream {
        final buffer = new ByteArrayOutputStream()

        @Override
        void write(int b) throws IOException {
            synchronized (buffer) {
                buffer.write(b)
            }
        }

        @Override
        String toString() {
            synchronized (buffer) {
                return buffer.toString()
            }
        }
    }
}
