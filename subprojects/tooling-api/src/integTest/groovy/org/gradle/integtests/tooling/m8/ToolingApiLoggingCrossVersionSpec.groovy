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

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.internal.consumer.ConnectorServices

@MinToolingApiVersion('1.0-milestone-8')
@MinTargetGradleVersion('1.0-milestone-8')
class ToolingApiLoggingCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        //for embedded tests we don't mess with global logging. Run with forks only.
        toolingApi.isEmbedded = false
        new ConnectorServices().reset()
    }

    def cleanup() {
        new ConnectorServices().reset()
    }

    def "logs necessary information when verbose"() {
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
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        when:
        withConnection {
            it.newBuild()
                .setStandardOutput(output)
                .setStandardError(error)
                .run()
        }

        then:
        def out = output.toString()
        out.count("debug logging yyy") == 1
        out.count("info logging yyy") == 1
        out.count("quiet logging yyy") == 1
        out.count("lifecycle logging yyy") == 1
        out.count("warn logging yyy") == 1
        out.count("println logging yyy") == 1
        out.count("error logging xxx") == 0

        shouldNotContainProviderLogging(out)

        def err = error.toString()
        err.count("error logging") == 1
        err.toString().count("sys err") == 1
        err.toString().count("logging yyy") == 0

        shouldNotContainProviderLogging(err)
    }

    def "logs necessary information"() {
        toolingApi.verboseLogging = false

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
        def output = new ByteArrayOutputStream()
        def error = new ByteArrayOutputStream()
        when:
        withConnection {
            it.newBuild()
                    .setStandardOutput(output)
                    .setStandardError(error)
                    .run()
        }

        then:
        def out = output.toString()
        out.count("debug logging yyy") == 0
        out.count("info logging yyy") == 0
        out.count("quiet logging yyy") == 1
        out.count("lifecycle logging yyy") == 1
        out.count("warn logging yyy") == 1
        out.count("println logging yyy") == 1
        out.count("error logging xxx") == 0

        shouldNotContainProviderLogging(out)

        def err = error.toString()
        err.count("error logging") == 1
        err.count("sys err") == 1
        err.count("logging yyy") == 0

        shouldNotContainProviderLogging(err)
    }

    void shouldNotContainProviderLogging(String output) {
        assert !output.contains("Provider implementation created.")
        assert !output.contains("Tooling API uses target gradle version:")
    }
}
