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

package org.gradle.integtests.tooling.m8

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.logging.GroupedOutputFixture
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiLoggingSpecification
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.GradleVersion

@LeaksFileHandles
class ToolingApiLoggingCrossVersionSpec extends ToolingApiLoggingSpecification {

    def setup() {
        toolingApi.requireIsolatedToolingApi()
    }

    def cleanup() {
        toolingApi.close()
    }

    def "client receives same stdout and stderr when in verbose mode as if running from the command-line in debug mode"() {
        toolingApi.verboseLogging = true

        file("build.gradle") << """
System.err.println "sys err logging xxx"

println "println logging yyy"

project.logger.error("error logging xxx");
project.logger.warn("warn logging yyy");
project.logger.lifecycle("lifecycle logging yyy");
project.logger.quiet("quiet logging yyy");
project.logger.info ("info logging yyy");
project.logger.debug("debug logging yyy");
"""
        when:
        def stdOut = new TestOutputStream()
        def stdErr = new TestOutputStream()
        withConnection {
            def build = it.newBuild()
            build.standardOutput = new TeeOutputStream(stdOut, System.out)
            build.standardError = new TeeOutputStream(stdErr, System.err)
            build.run()
        }

        then:
        def out = stdOut.toString()
        def err = stdErr.toString()

        out.count("debug logging yyy") == 1
        out.count("info logging yyy") == 1
        out.count("quiet logging yyy") == 1
        out.count("lifecycle logging yyy") == 1
        out.count("warn logging yyy") == 1
        out.count("println logging yyy") == 1
        if (targetVersion.baseVersion >= GradleVersion.version("4.7")) {
            // Handling of error log message changed
            out.count("error logging xxx") == 1
            out.count("sys err logging xxx") == 1

            err.count("logging") == 0
        }  else {
            out.count("logging xxx") == 0

            err.count("logging yyy") == 0
            err.count("error logging xxx") == 1
            err.count("sys err logging xxx") == 1
        }

        and:
        shouldNotContainProviderLogging(out)
        shouldNotContainProviderLogging(err)
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
        def commandLineResult = runUsingCommandLine()

        and:
        def op = withBuild()

        then:
        def out = op.result.output
        def err = op.result.error
        def commandLineOutput = removeStartupWarnings(commandLineResult.output)
        normaliseOutput(out) == normaliseOutput(commandLineOutput)
        err == commandLineResult.error

        and:
        def errLogging
        if (targetDist.toolingApiMergesStderrIntoStdout) {
            errLogging = out
        } else {
            errLogging = err
        }
        errLogging.count("System.err \u03b1\u03b2") == 1
        errLogging.count("error logging \u03b1\u03b2") == 1

        and:
        out.count("lifecycle logging \u03b1\u03b2") == 1
        out.count("warn logging") == 1
        out.count("quiet logging") == 1
        out.count("info") == 0
        out.count("debug") == 0

        err.count("warn") == 0
        err.count("quiet") == 0
        err.count("lifecycle") == 0
        err.count("info") == 0
        err.count("debug") == 0
    }

    private static removeStartupWarnings(String output) {
        while (output.startsWith('Starting a Gradle Daemon') || output.startsWith('Parallel execution is an incubating feature.')) {
            output = output.substring(output.indexOf('\n') + 1)
        }
        output
    }

    private ExecutionResult runUsingCommandLine() {
        def executer = targetDist.executer(temporaryFolder, getBuildContext())
            .withCommandLineGradleOpts("-Dorg.gradle.deprecation.trace=false") //suppress deprecation stack trace

        if (targetDist.toolingApiMergesStderrIntoStdout) {
            // The TAPI provider merges the streams, so need to merge the streams for command-line execution too
            executer.withArgument("--console=plain")
            executer.withTestConsoleAttached()
            // We changed the test console system property values in 4.9, need to use "both" instead of "BOTH"
            if (targetVersion.baseVersion >= GradleVersion.version("4.8")
                    && targetVersion.baseVersion < GradleVersion.version("4.9")) {
                executer.withCommandLineGradleOpts("-Dorg.gradle.internal.console.test-console=both")
            }
        }

        return executer.run()
    }

    String normaliseOutput(String output) {
        // Must replace both build result formats for cross compat
        return output
            .replaceAll(/Unable to list file systems to check whether they can be watched.*\n/, '')
            .replaceFirst(/Support for .* was deprecated.*\n/, '')
            .replaceFirst(/ in [ \dms]+/, " in 0ms")
            .replaceFirst("Total time: .+ secs", "Total time: 0 secs")
            .replaceAll(GroupedOutputFixture.PLAIN_PROGRESS_REPORT_PATTERN, '')
    }

    void shouldNotContainProviderLogging(String output) {
        assert !output.contains("Provider implementation created.")
        assert !output.contains("Tooling API uses target gradle version:")
        assert !output.contains("Tooling API is using target Gradle version:")
    }
}
