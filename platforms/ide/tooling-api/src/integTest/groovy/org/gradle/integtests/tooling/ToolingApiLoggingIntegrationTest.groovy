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

package org.gradle.integtests.tooling

import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@LeaksFileHandles
@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "because toolingApi.requireIsolatedToolingApi()")
class ToolingApiLoggingIntegrationTest extends AbstractIntegrationSpec {

    ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)

    def setup() {
        toolingApi.requireIsolatedToolingApi()
    }

    def cleanup() {
        toolingApi.close()
    }

    def "client receives same stdout and stderr when in verbose mode as if running from the command-line in debug mode"() {
        toolingApi.verboseLogging = true

        settingsFile.touch()
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
        toolingApi.withConnection {
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
        out.count("logging xxx") == 0

        err.count("logging yyy") == 0
        err.count("error logging xxx") == 1
        err.count("sys err logging xxx") == 1

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
        succeeds("help")

        and:
        def stdOut = new TestOutputStream()
        def stdErr = new TestOutputStream()
        toolingApi.withConnection {
            def builder = newBuild().forTasks("help")
                .setStandardOutput(new TeeOutputStream(stdOut, System.out))
                .setStandardError(new TeeOutputStream(stdErr, System.err))

            if (GradleContextualExecuter.configCache) {
                builder.addArguments("--configuration-cache")
            }

            builder.run()
        }

        then:
        def out = stdOut.toString()
        def err = stdErr.toString()
        normalizeTapi(out) == normalizeCmdline(result.output)
        TextUtil.normaliseLineSeparators(err) == TextUtil.normaliseLineSeparators(result.error)

        and:
        err.count("System.err \u03b1\u03b2") == 1
        err.count("error logging \u03b1\u03b2") == 1

        and:
        out.count("lifecycle logging \u03b1\u03b2") == 1
        out.count("warn logging") == 1
        out.count("quiet logging") == 1
        out.count("info logging") == 0
        out.count("debug logging") == 0

        err.count("warn") == 0
        err.count("quiet") == 0
        err.count("lifecycle") == 0
        err.count("info") == 0
        err.count("debug") == 0
    }

    private static String normalizeTapi(String output) {
        while (
            output.startsWith('Calculating task graph as no cached configuration is available for tasks: help')
        ) {
            output = output.substring(output.indexOf('\n') + 1)
        }
        normalize(output)
    }

    private static normalizeCmdline(String output) {
        while (
            output.startsWith('Parallel Configuration Cache is an incubating feature.')
        ) {
            output = output.substring(output.indexOf('\n') + 1)
        }
        normalize(output)
    }

    private static normalize(String output) {
        while (output.startsWith('Starting a Gradle Daemon')) {
            output = output.substring(output.indexOf('\n') + 1)
        }
        TextUtil.normaliseLineSeparators(output)
        // Must replace both build result formats for cross compat
            .replaceAll(/Unable to list file systems to check whether they can be watched.*\n/, '')
            .replaceFirst(/Parallel Configuration Cache is an incubating feature.\n/, '')
            .replaceFirst(/Support for .* was deprecated.*\n/, '')
            .replaceFirst(/ in [ \dms]+/, " in 0ms")
            .replaceFirst("Total time: .+ secs", "Total time: 0 secs")
            .replaceFirst(/(?s)To honour the JVM settings for this build a (new JVM|single-use Daemon process) will be forked.+will be stopped at the end of the build (stopping after processing)?\n/, "")
    }

    void shouldNotContainProviderLogging(String output) {
        assert !output.contains("Provider implementation created.")
        assert !output.contains("Tooling API uses target gradle version:")
        assert !output.contains("Tooling API is using target Gradle version:")
    }
}
