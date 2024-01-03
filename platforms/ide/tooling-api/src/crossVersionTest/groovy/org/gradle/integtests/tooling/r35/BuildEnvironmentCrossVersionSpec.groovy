/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r35

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.build.BuildEnvironment

class BuildEnvironmentCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=3.5")
    @TargetGradleVersion(">=3.5")
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "old versions can mutate environment on JDK < 9"() {
        given:
        toolingApi.requireDaemons() //cannot be run in embedded mode

        buildFile << """
            task printEnv() {
                doLast {
                    println "<" + System.getenv() + ">"
                }
            }"""

        when:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            connection.newBuild().setEnvironmentVariables(["var": "val"]).setStandardOutput(out).forTasks('printEnv').run()
        }

        then:
        out.toString().contains("<${["var": "val"]}>")
    }

    @ToolingApiVersion(">=4.11")
    @TargetGradleVersion(">=4.11")
    def "new Gradle versions can mutate environment on all JDK versions"() {
        given:
        toolingApi.requireDaemons() //cannot be run in embedded mode

        buildFile << """
            task printEnv() {
                doLast {
                    println "<" + System.getenv() + ">"
                }
            }"""

        when:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        withConnection { ProjectConnection connection ->
            connection.newBuild().setEnvironmentVariables(["var": "val"]).setStandardOutput(out).forTasks('printEnv').run()
        }

        then:
        out.toString().contains("<${["var": "val"]}>")
    }

    @ToolingApiVersion(">=3.5")
    @TargetGradleVersion(">=2.6 <3.5")
    def "long running operation should fail when environment vars specified but not supported by target"() {
        when:
        withConnection {
            def model = it.model(BuildEnvironment.class)
            model.setEnvironmentVariables(["var": "val"]).get()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${targetDist.version.version}) does not support the environment variables customization feature. Support for this is available in Gradle 3.5 and all later versions."
    }
}
