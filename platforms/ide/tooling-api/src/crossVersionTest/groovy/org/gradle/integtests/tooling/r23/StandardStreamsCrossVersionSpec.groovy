/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r23


import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiLoggingSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule

class StandardStreamsCrossVersionSpec extends ToolingApiLoggingSpecification {
    @Rule RedirectStdOutAndErr stdOutAndErr = new RedirectStdOutAndErr()
    def escapeHeader = "\u001b["

    def "logging is not sent to System.out or System.err"() {
        file("build.gradle") << """
project.logger.error("error log message");
project.logger.warn("warn log message");
project.logger.lifecycle("lifecycle log message");
project.logger.quiet("quiet log message");
project.logger.info ("info log message");
project.logger.debug("debug log message");

task log {
    doLast {
        println "task log message"
    }
}
"""

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks("log")
            build.run()
        }

        then:
        !stdOutAndErr.stdOut.contains("log message")
        !stdOutAndErr.stdErr.contains("log message")
    }

    def "logging is not sent to System.out or System.err when using custom output streams"() {
        file("build.gradle") << """
project.logger.error("error logging");
project.logger.warn("warn logging");
project.logger.lifecycle("lifecycle logging");
project.logger.quiet("quiet logging");
project.logger.info ("info logging");
project.logger.debug("debug logging");

task log {
    doLast {
        println "task logging"
    }
}
"""

        when:
        def output = new TestOutputStream()
        def error = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.standardError = error
            build.forTasks("log")
            build.run()
        }

        then:
        output.toString().contains("task logging")
        output.toString().contains("warn logging")
        output.toString().contains("lifecycle logging")
        output.toString().contains("quiet logging")
        if (targetVersion.baseVersion >= GradleVersion.version('4.7')) {
            // Changed handling of error log messages
            output.toString().contains("error logging")
        } else {
            error.toString().contains("error logging")
        }

        and:
        !stdOutAndErr.stdOut.contains("logging")
        !stdOutAndErr.stdErr.contains("logging")
    }

    def "can specify color output when output is being ignored"() {
        file("build.gradle") << """
task log {
    outputs.upToDateWhen { true }
}
"""

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.colorOutput = true
            build.forTasks("log")
            build.run()
        }

        then:
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)
    }
}
