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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import org.gradle.util.TextUtil

import static org.gradle.api.JavaVersion.VERSION_1_8
import static org.gradle.api.JavaVersion.VERSION_1_9

@Requires(adhoc = { AvailableJavaHomes.getJdk(VERSION_1_8) && AvailableJavaHomes.getJdk(VERSION_1_9) })
class GroovyCompileJavaVersionTrackingIntegrationTest extends AbstractIntegrationSpec {

    /**
     * When running in embedded mode, core tasks are loaded from the runtime classloader.
     * When running in the daemon, they are loaded from the plugins classloader.
     * This difference leads to different up-to-date messages, which is why we force
     * a consistent execution mode.
     */
    def setup() {
        file("src/main/groovy/org/gradle/Person.groovy") << """
            package org.gradle
            class Person {}
        """
        executer.requireDaemon().requireIsolatedDaemons()
    }

    def "tracks changes to the Groovy compiler JVM Java version"() {
        given:
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)
        def jdk9 = AvailableJavaHomes.getJdk(VERSION_1_9)

        compileWithJavaJdk(jdk8)

        when:
        executer.withJavaHome jdk9.javaHome
        succeeds ":compileGroovy"
        then:
        executedAndNotSkipped ":compileGroovy"

        when:
        executer.withJavaHome jdk9.javaHome
        succeeds ":compileGroovy"
        then:
        skipped ":compileGroovy"

        when:
        executer.withJavaHome jdk8.javaHome
        succeeds ":compileGroovy", "--info"
        then:
        executedAndNotSkipped ":compileGroovy"
        output.contains "Value of input property 'groovyCompilerJvmVersion' has changed for task ':compileGroovy'"
    }

    @ToBeFixedForInstantExecution
    def "tracks changes to the Java toolchain used for cross compilation"() {
        given:
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)
        def jdk9 = AvailableJavaHomes.getJdk(VERSION_1_9)

        compileWithJavaJdk(jdk8)

        when:
        executer.withJavaHome jdk9.javaHome
        succeeds "compileGroovy"
        then:
        executedAndNotSkipped ":compileGroovy"

        when:
        compileWithJavaJdk(jdk9)
        executer.withJavaHome jdk9.javaHome
        succeeds "compileGroovy", "--info"
        then:
        executedAndNotSkipped ":compileGroovy"
        output.contains "Value of input property 'javaToolChain.version' has changed for task ':compileGroovy'"
    }

    private void compileWithJavaJdk(Jvm jdk) {
        def javaHome = TextUtil.escapeString(jdk.getJavaHome().absolutePath)
        buildFile << """
            apply plugin: "groovy"

            sourceCompatibility = "1.7"
            targetCompatibility = "1.7"

            dependencies {
                implementation localGroovy()
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
