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
import org.gradle.util.Requires
import org.gradle.util.TextUtil

import static org.gradle.api.JavaVersion.VERSION_1_7
import static org.gradle.api.JavaVersion.VERSION_1_8

@Requires(adhoc = { AvailableJavaHomes.getJdk(VERSION_1_7) && AvailableJavaHomes.getJdk(VERSION_1_8) })
class JavaCompileJavaVersionIntegrationTest extends AbstractIntegrationSpec {

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
        executer.withJavaHome AvailableJavaHomes.getJdk(VERSION_1_7).javaHome
        succeeds "compileJava"
        then:
        nonSkippedTasks.contains ":compileJava"

        when:
        executer.withJavaHome AvailableJavaHomes.getJdk(VERSION_1_7).javaHome
        succeeds "compileJava"
        then:
        skippedTasks.contains ":compileJava"

        when:
        executer.withJavaHome AvailableJavaHomes.getJdk(VERSION_1_8).javaHome
        succeeds "compileJava", "--info"
        then:
        nonSkippedTasks.contains ":compileJava"
        output.contains "Value of input property 'toolChain.version' has changed for task ':compileJava'"
    }

    def "not up-to-date when java version for forking changes"() {
        given:
        def jdk7 = AvailableJavaHomes.getJdk(VERSION_1_7)
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)


        buildFile << forkedJavaCompilation(jdk7)

        and:
        file("src/main/java/org/gradle/Person.java") << """
            package org.gradle;
            class Person {}
        """

        when:
        executer.withJavaHome jdk8.javaHome
        succeeds "compileJava"
        then:
        nonSkippedTasks.contains ":compileJava"

        when:
        executer.withJavaHome jdk7.javaHome
        succeeds "compileJava"
        then:
        skippedTasks.contains ":compileJava"

        when:
        executer.withJavaHome jdk7.javaHome
        buildFile.text = forkedJavaCompilation(jdk8)
        succeeds "compileJava", "--info"
        then:
        nonSkippedTasks.contains ":compileJava"
        output.contains "Value of input property 'toolChain.version' has changed for task ':compileJava'"
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
