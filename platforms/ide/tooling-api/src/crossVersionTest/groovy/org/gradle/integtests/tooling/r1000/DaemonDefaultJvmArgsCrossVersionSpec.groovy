/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r1000

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.build.BuildEnvironment

import java.lang.management.ManagementFactory

@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=10.0")
@Requires(
    value = TestExecutionPreconditions.NotEmbeddedExecutor,
    reason = "In order to pass JVM arguments to the Gradle daemon, we need to use the external executor."
)
class DaemonDefaultJvmArgsCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile """
            file("jvm-args.txt").text = ${ManagementFactory.name}.runtimeMXBean.inputArguments.join("\\n")
        """
    }

    def "merges JVM arguments set and added via the Tooling API with daemon defaults"() {
        when:
        withConnection { ProjectConnection connection ->
            connection.newBuild()
                .forTasks("help")
                .setJvmArguments("-Xmx1024m")
                .addJvmArguments("-XX:-HeapDumpOnOutOfMemoryError")
                .run()
        }
        def jvmArgs = file("jvm-args.txt").text.readLines()

        then:
        jvmArgs.containsAll(["-Xmx1024m", "-XX:MaxMetaspaceSize=768m", "-XX:-HeapDumpOnOutOfMemoryError"])
        !jvmArgs.contains("-Xmx512m")
        !jvmArgs.contains("-Xms256m")
        !jvmArgs.contains("-XX:+HeapDumpOnOutOfMemoryError")
    }

    def "build environment reports JVM arguments merged with daemon defaults"() {
        given:
        propertiesFile << "org.gradle.jvmargs=-Xmx1024m"

        when:
        def jvmArguments = withConnection { ProjectConnection connection ->
            connection.getModel(BuildEnvironment).java.jvmArguments
        }

        then:
        jvmArguments.containsAll(["-Xmx1024m", "-XX:MaxMetaspaceSize=768m", "-XX:+HeapDumpOnOutOfMemoryError"])
        !jvmArguments.contains("-Xmx512m")
    }
}
