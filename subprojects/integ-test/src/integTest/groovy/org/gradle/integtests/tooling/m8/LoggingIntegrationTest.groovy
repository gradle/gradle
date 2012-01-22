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

@MinToolingApiVersion('1.0-milestone-8')
@MinTargetGradleVersion('1.0-milestone-8')
class LoggingIntegrationTest extends ToolingApiSpecification {

    def setup() {
        //for embedded tests we don't mess with global logging. Run with forks only.
        toolingApi.isEmbedded = false
    }

    def "logs necessary information when verbose"() {
        toolingApi.verboseLogging = true

        dist.file("build.gradle") << """
System.err.println "sys err logging xxx"

println "println logging yyy"

project.logger.error("error logging xxx");
project.logger.warn("warn logging yyy");
project.logger.lifecycle("lifecycle logging yyy");
project.logger.quiet("quiet logging yyy");
project.logger.info ("info logging yyy");
project.logger.debug("debug logging yyy");
"""
        def out = new ByteArrayOutputStream()
        def err = new ByteArrayOutputStream()
        when:
        withConnection {
            it.newBuild()
                .setStandardOutput(out)
                .setStandardError(err)
                .run()
        }

        then:
        def output = out.toString()
        output.count("debug logging yyy") == 1
        output.count("info logging yyy") == 1
        output.count("quiet logging yyy") == 1
        output.count("lifecycle logging yyy") == 1
        output.count("warn logging yyy") == 1
        output.count("println logging yyy") == 1
        output.count("error logging xxx") == 0

        err.toString().count("error logging") == 1
        err.toString().count("sys err") == 1
        err.toString().count("logging yyy") == 0
    }

    def "logs necessary information"() {
        toolingApi.verboseLogging = false

        dist.file("build.gradle") << """
System.err.println "sys err logging xxx"

println "println logging yyy"

project.logger.error("error logging xxx");
project.logger.warn("warn logging yyy");
project.logger.lifecycle("lifecycle logging yyy");
project.logger.quiet("quiet logging yyy");
project.logger.info ("info logging yyy");
project.logger.debug("debug logging yyy");
"""
        def out = new ByteArrayOutputStream()
        def err = new ByteArrayOutputStream()
        when:
        withConnection {
            it.newBuild()
                    .setStandardOutput(out)
                    .setStandardError(err)
                    .run()
        }

        then:
        def output = out.toString()
        output.count("debug logging yyy") == 0
        output.count("info logging yyy") == 0
        output.count("quiet logging yyy") == 1
        output.count("lifecycle logging yyy") == 1
        output.count("warn logging yyy") == 1
        output.count("println logging yyy") == 1
        output.count("error logging xxx") == 0

        err.toString().count("error logging") == 1
        err.toString().count("sys err") == 1
        err.toString().count("logging yyy") == 0
    }
}
