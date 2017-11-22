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


package org.gradle.integtests.tooling.r42

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.logging.DeprecationReport
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r18.NullAction
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.model.GradleProject
import spock.lang.IgnoreIf

@TargetGradleVersion("current")
class ToolingApiDeprecatedBuildJvmCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        toolingApi.requireDaemons()
    }

    def configureJava7(){
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk7.javaHome.absolutePath)
    }

    def configureJava8(){
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.absolutePath)
    }

    def getDeprecationReport(){
        new DeprecationReport(projectDir)
    }

    def warningCount(def output){
        return deprecationReport.count("Support for running Gradle using Java 7 has been deprecated and is scheduled to be removed in Gradle 5.0")
    }

    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    def "warning running a build when build is configured to use Java 7"() {
        configureJava7()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.run()
        }

        then:
        warningCount(output) == 1
    }

    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    def "warning fetching model when build is configured to use Java 7"() {
        configureJava7()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject)
            model.standardOutput = output
            model.get()
        }

        then:
        warningCount(output) == 1
    }

    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    def "warning running action when build is configured to use Java 7"() {
        configureJava7()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def action = connection.action(new NullAction())
            action.standardOutput = output
            action.run()
        }

        then:
        warningCount(output) == 1
    }

    @ToolingApiVersion(">=2.6")
    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    def "warning running tests when build is configured to use Java 7"() {
        configureJava7()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("SomeTest")
            launcher.standardOutput = output
            launcher.run()
        }

        then:
        TestExecutionException e = thrown()
        e.cause.message.startsWith("No matching tests found in any candidate test task.")
        warningCount(output) == 1
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "no warning running a build when build is configured to use Java 8"() {
        configureJava8()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.run()
        }

        then:
        warningCount(output) == 0
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "no warning fetching model when build is configured to use Java 8"() {
        configureJava8()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def model = connection.model(GradleProject)
            model.standardOutput = output
            model.get()
        }

        then:
        warningCount(output) == 0
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "no warning running action when build is configured to use Java 8"() {
        configureJava8()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def action = connection.action(new NullAction())
            action.standardOutput = output
            action.run()
        }

        then:
        warningCount(output) == 0
    }

    @ToolingApiVersion(">=2.6")
    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "no warning running tests when build is configured to use Java 8"() {
        configureJava8()
        def output = new ByteArrayOutputStream()

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def launcher = connection.newTestLauncher().withJvmTestClasses("SomeTest")
            launcher.standardOutput = output
            launcher.run()
        }

        then:
        TestExecutionException e = thrown()
        e.cause.message.startsWith("No matching tests found in any candidate test task.")
        warningCount(output) == 0
    }
}
