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

package org.gradle.language.groovy

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import org.gradle.util.TextUtil

import static org.gradle.api.JavaVersion.VERSION_1_7
import static org.gradle.api.JavaVersion.VERSION_1_8

@Requires(adhoc = { AvailableJavaHomes.getJdk(VERSION_1_7) && AvailableJavaHomes.getJdk(VERSION_1_8) })
class GroovyCompileJavaVersionTrackingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        file("src/main/groovy/org/gradle/Person.groovy") << """
            package org.gradle
            class Person {}
        """
    }

    def "tracks changes to the Groovy compiler JVM Java version"() {
        given:
        def jdk7 = AvailableJavaHomes.getJdk(VERSION_1_7)
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)

        compileWithJavaJdk(jdk7)

        when:
        executer.withJavaHome jdk8.javaHome
        succeeds ":compileGroovy"
        then:
        nonSkippedTasks.contains ":compileGroovy"

        when:
        executer.withJavaHome jdk8.javaHome
        succeeds ":compileGroovy"
        then:
        skippedTasks.contains ":compileGroovy"

        when:
        executer.withJavaHome jdk7.javaHome
        succeeds ":compileGroovy", "--info"
        then:
        nonSkippedTasks.contains ":compileGroovy"
        output.contains "Value of input property 'groovyCompilerJvmJavaVersion' has changed for task ':compileGroovy'"
    }

    @NotYetImplemented
    def "tracks changes to the Java toolchain used for cross compilation"() {
        given:
        def jdk7 = AvailableJavaHomes.getJdk(VERSION_1_7)
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)

        compileWithJavaJdk(jdk7)

        when:
        executer.withJavaHome jdk8.javaHome
        succeeds "compileGroovy"
        then:
        nonSkippedTasks.contains ":compileGroovy"

        when:
        compileWithJavaJdk(jdk8)
        executer.withJavaHome jdk8.javaHome
        succeeds "compileGroovy"
        then:
        nonSkippedTasks.contains ":compileGroovy"
    }

    private void compileWithJavaJdk(Jvm jdk) {
        def javaHome = TextUtil.escapeString(jdk.getJavaHome().absolutePath)
        buildFile << """
            apply plugin: "groovy"

            sourceCompatibility = "1.7"
            targetCompatibility = "1.7"
               
            dependencies {
                compile localGroovy()
            }

            compileGroovy {
                options.with {
                    fork = true
                    forkOptions.javaHome=file('${javaHome}')
                }
            }
            
        """
    }
}
