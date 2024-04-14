/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r88

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.junit.Assume

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk17
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk21
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk8
import static org.gradle.integtests.tooling.r86.ProblemsServiceModelBuilderCrossVersionTest.getBuildScriptSampleContent

@TargetGradleVersion(">=8.8")
@ToolingApiVersion("=8.8")
class ProblemsServiceModelBuilderCrossVersionTest extends ToolingApiSpecification {

    org.gradle.integtests.tooling.r87.ProblemProgressEventCrossVersionTest.ProblemProgressListener listener

    def setup(){
        listener = new org.gradle.integtests.tooling.r87.ProblemProgressEventCrossVersionTest.ProblemProgressListener()
    }

    def "Can use problems service in model builder and get failure objects"() {
        given:
        Assume.assumeTrue(javaHome != null)
        buildFile getBuildScriptSampleContent(false, false, targetVersion)

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(javaHome.javaHome)
                .addProgressListener(listener)
                .get()
        }
        def problems = getProblems()

        then:
        problems.size() == 0

        where:
        javaHome << [
            jdk8,
            jdk17,
            jdk21
        ]
    }

    def "Can add additional metadata"() {
        given:
        buildFile getBuildScriptSampleContent(false, true, targetVersion)

        when:
        withConnection { connection ->
            connection.model(CustomModel)
                .addProgressListener(listener)
                .get()
        }


        then:
        def problems = getProblems()
        problems.size() == 0
    }

    List<Object> getProblems() {
        listener.problems.collect { it as SingleProblemEvent }
    }
}
