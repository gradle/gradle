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
import org.gradle.tooling.ProjectConnection

class BuildEnvironmentCrossVersionSpec extends ToolingApiSpecification {

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
}
