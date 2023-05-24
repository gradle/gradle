/*
 * Copyright 2017 the original author or authors.
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


package org.gradle.integtests.tooling.r44

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

@Issue('https://github.com/gradle/gradle/issues/3317')
@TargetGradleVersion(">=3.0 <4.2.1")
class JavaVersionCrossVersionTest extends ToolingApiSpecification {
    def configureJava8() {
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.absolutePath)
    }

    @Requires([UnitTestPreconditions.Jdk9OrLater, IntegTestPreconditions.Java8HomeAvailable])
    def "smoke test for JavaVersion scheme patch"() {
        configureJava8()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.forTasks('help')
            build.standardOutput = output
            build.run()
        }

        then:
        output.toString().count("Welcome to Gradle")
    }
}
