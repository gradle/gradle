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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule

class StandardStreamsCrossVersionSpec extends ToolingApiSpecification {
    @Rule RedirectStdOutAndErr stdOutAndErr = new RedirectStdOutAndErr()
    def escapeHeader = "\u001b["

    def setup() {
        toolingApi.requireDaemons()
    }

    @TargetGradleVersion(">=2.3")
    def "logging is not sent to System.out or System.err"() {
        String uuid = UUID.randomUUID().toString()
        file("build.gradle") << """
project.logger.error("error logging-${uuid}");
project.logger.warn("warn logging-${uuid}");
project.logger.lifecycle("lifecycle logging-${uuid}");
project.logger.quiet("quiet logging-${uuid}");
project.logger.info ("info logging-${uuid}");
project.logger.debug("debug logging-${uuid}");

task log << {
    println "task logging-${uuid}"
}
"""

        when:
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks("log")
            build.run()
        }

        then:
        !stdOutAndErr.stdOut.contains(uuid)
        !stdOutAndErr.stdErr.contains(uuid)
    }

    @TargetGradleVersion(">=2.3")
    def "logging is not sent to System.out or System.err when using custom output streams"() {
        String uuid = UUID.randomUUID().toString()
        file("build.gradle") << """
project.logger.error("error logging-${uuid}");
project.logger.warn("warn logging-${uuid}");
project.logger.lifecycle("lifecycle logging-${uuid}");
project.logger.quiet("quiet logging-${uuid}");
project.logger.info ("info logging-${uuid}");
project.logger.debug("debug logging-${uuid}");

task log << {
    println "task logging-${uuid}"
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
        output.toString().contains("task logging-" + uuid)
        output.toString().contains("warn logging-${uuid}")
        output.toString().contains("lifecycle logging-${uuid}")
        output.toString().contains("quiet logging-${uuid}")
        error.toString().contains("error logging-${uuid}")
        !stdOutAndErr.stdOut.contains(uuid)
        !stdOutAndErr.stdErr.contains(uuid)
    }

    @ToolingApiVersion(">=2.3")
    @TargetGradleVersion(">=2.3")
    def "can specify color output"() {
        file("build.gradle") << """
task log {
    outputs.upToDateWhen { true }
}
"""

        when:
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.colorOutput = true
            build.forTasks("log")
            build.run()
        }

        then:
        output.toString().contains("UP-TO-DATE" + escapeHeader)
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)
    }

    @ToolingApiVersion(">=2.3")
    @TargetGradleVersion("<2.3")
    def "can specify color output when target version does not support colored output"() {
        file("build.gradle") << """
task log {
    outputs.upToDateWhen { true }
}
"""

        when:
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.colorOutput = true
            build.forTasks("log")
            build.run()
        }

        then:
        !output.toString().contains(escapeHeader)
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)
    }

    @ToolingApiVersion(">=2.3")
    @TargetGradleVersion(">=2.3")
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
