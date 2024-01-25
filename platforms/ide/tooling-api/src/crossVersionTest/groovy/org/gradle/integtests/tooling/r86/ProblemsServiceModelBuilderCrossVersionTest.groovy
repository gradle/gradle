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

package org.gradle.integtests.tooling.r86

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.integtests.tooling.r85.ProblemsServiceModelBuilderCrossVersionTest.ProblemProgressListener
import org.junit.Assume

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk17
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk21
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk8
import static org.gradle.integtests.tooling.r85.ProblemsServiceModelBuilderCrossVersionTest.getBuildScriptSampleContent

@ToolingApiVersion(">=8.6")
class ProblemsServiceModelBuilderCrossVersionTest extends ToolingApiSpecification {

    @TargetGradleVersion("=8.6")
    def "Can use problems service in model builder and get problem"() {
        given:
        Assume.assumeTrue(jdk != null)
        buildFile getBuildScriptSampleContent(false, false)
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(jdk.javaHome)
                .addProgressListener(listener)
                .get()
        }
        def problems = listener.problems

        then:
        problems.size() == 1

        where:
        jdk << [
            jdk8,
            jdk17,
            jdk21
        ]
    }
}
