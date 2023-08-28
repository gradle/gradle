/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.api.JavaVersion.VERSION_1_8

class JavaExecJavaVersionIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        // Without this, executing the JavaExec tasks leave behind running daemons.
        executer.requireDaemon().requireIsolatedDaemons()
    }

    @Requires([IntegTestPreconditions.LowestSupportedLTSJavaHomeAvailable, IntegTestPreconditions.HighestSupportedLTSJavaHomeAvailable ])
    def "up-to-date when executing twice in a row"() {
        given:
        setupRunHelloWorldTask()

        when:
        executer.withJavaHome AvailableJavaHomes.getLowestSupportedLTS().javaHome
        succeeds "runHelloWorld"
        then:
        executedAndNotSkipped ":runHelloWorld"

        when:
        executer.withJavaHome AvailableJavaHomes.getLowestSupportedLTS().javaHome
        succeeds "runHelloWorld"
        then:
        skipped ":runHelloWorld"
    }

    @Issue("https://github.com/gradle/gradle/issues/6694")
    @Requires([IntegTestPreconditions.LowestSupportedLTSJavaHomeAvailable, IntegTestPreconditions.HighestSupportedLTSJavaHomeAvailable ])
    def "not up-to-date when the Java version changes"() {
        given:
        setupRunHelloWorldTask()

        when:
        executer.withJavaHome AvailableJavaHomes.getLowestSupportedLTS().javaHome
        succeeds "runHelloWorld"
        then:
        executedAndNotSkipped ":runHelloWorld"

        when:
        executer.withJavaHome AvailableJavaHomes.getHighestSupportedLTS().javaHome
        succeeds "runHelloWorld", "--info"
        then:
        executedAndNotSkipped ":runHelloWorld"
        output.contains "Value of input property 'javaVersion' has changed for task ':runHelloWorld'"
    }

    @Issue("https://github.com/gradle/gradle/issues/6694")
    @Requires(IntegTestPreconditions.MoreThanOneJava8HomeAvailable)
    def "up-to-date when the Java executable changes but the version does not"() {
        given:
        setupRunHelloWorldTask()

        when:
        executer.withJavaHome AvailableJavaHomes.getAvailableJdks(VERSION_1_8)[0].javaHome
        succeeds "runHelloWorld"
        then:
        executedAndNotSkipped ":runHelloWorld"

        when:
        executer.withJavaHome AvailableJavaHomes.getAvailableJdks(VERSION_1_8)[1].javaHome
        succeeds "runHelloWorld"
        then:
        skipped ":runHelloWorld"
    }

    private void setupRunHelloWorldTask() {
        buildScript '''
            apply plugin: "java"

            task runHelloWorld(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "Hello"
                outputs.dir "$buildDir/runHelloWorld"
            }
        '''

        file("src/main/java/Hello.java") << '''
            public class Hello {
                public static void main(String... args) {
                    // Generate files into "$buildDir/runHelloWorld"
                }
            }
        '''
    }
}
