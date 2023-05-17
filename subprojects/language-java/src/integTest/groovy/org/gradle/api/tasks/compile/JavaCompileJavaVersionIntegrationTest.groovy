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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

import static org.gradle.api.JavaVersion.VERSION_1_8
import static org.gradle.api.JavaVersion.VERSION_1_9

@Requires([IntegTestPreconditions.Java8HomeAvailable, IntegTestPreconditions.Java9HomeAvailable ])
class JavaCompileJavaVersionIntegrationTest extends AbstractIntegrationSpec {

    /**
     * When running in embedded mode, core tasks are loaded from the runtime classloader.
     * When running in the daemon, they are loaded from the plugins classloader.
     * This difference leads to different up-to-date messages, which is why we force
     * a consistent execution mode.
     */
    def setup() {
        executer.requireDaemon().requireIsolatedDaemons()
    }

    def "not up-to-date when default Java version changes"() {
        given:
        buildFile << """
            apply plugin: "java"

            sourceCompatibility = "1.6"
            targetCompatibility = "1.6"
        """

        and:
        file("src/main/java/org/gradle/Person.java") << """
            package org.gradle;
            class Person {}
        """

        when:
        executer.withJavaHome AvailableJavaHomes.getJdk(VERSION_1_8).javaHome
        succeeds "compileJava"
        then:
        executedAndNotSkipped ":compileJava"

        when:
        executer.withJavaHome AvailableJavaHomes.getJdk(VERSION_1_8).javaHome
        succeeds "compileJava"
        then:
        skipped ":compileJava"

        when:
        executer.withJavaHome AvailableJavaHomes.getJdk(VERSION_1_9).javaHome
        succeeds "compileJava", "--info"
        then:
        executedAndNotSkipped ":compileJava"
        output.contains "Value of input property 'javaVersion' has changed for task ':compileJava'"
    }

    def "not up-to-date when java version for forking changes"() {
        given:
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)
        def jdk9 = AvailableJavaHomes.getJdk(VERSION_1_9)


        buildFile << forkedJavaCompilation(jdk8)

        and:
        file("src/main/java/org/gradle/Person.java") << """
            package org.gradle;
            class Person {}
        """

        when:
        executer.withJavaHome jdk9.javaHome
        succeeds "compileJava"
        then:
        executedAndNotSkipped ":compileJava"

        when:
        executer.withJavaHome jdk8.javaHome
        succeeds "compileJava"
        then:
        skipped ":compileJava"

        when:
        executer.withJavaHome jdk8.javaHome
        buildFile.text = forkedJavaCompilation(jdk9)
        succeeds "compileJava", "--info"
        then:
        executedAndNotSkipped ":compileJava"
        output.contains "Value of input property 'javaVersion' has changed for task ':compileJava'"
    }

    private static String forkedJavaCompilation(Jvm jdk) {
        def javaHome = TextUtil.escapeString(jdk.getJavaHome().absolutePath)
        """
            apply plugin: "java"

            sourceCompatibility = "1.6"
            targetCompatibility = "1.6"

            compileJava {
                options.with {
                    fork = true
                    forkOptions.javaHome=file('${javaHome}')
                }
            }

        """
    }
}
