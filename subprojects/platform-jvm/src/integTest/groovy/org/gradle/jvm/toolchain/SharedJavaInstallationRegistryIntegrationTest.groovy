/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.jvm.toolchain

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm

class SharedJavaInstallationRegistryIntegrationTest extends AbstractIntegrationSpec {

    def "installation registry has only current vm without environment setup"() {
        buildFile << """
            import org.gradle.jvm.toolchain.internal.SharedJavaInstallationRegistry;
            import javax.inject.Inject

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract SharedJavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                        println "installations:" + registry.listInstallations()
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        succeeds("show")

        then:
        def currentVm = Jvm.current().getJavaHome().getAbsolutePath()
        outputContains("installations:[${currentVm}]")
    }

    def "installation registry is populated by environment"() {
        def firstJavaHome = AvailableJavaHomes.availableJvms[0].javaHome.absolutePath
        def secondJavaHome = AvailableJavaHomes.availableJvms[1].javaHome.absolutePath

        buildFile << """
            import org.gradle.jvm.toolchain.internal.SharedJavaInstallationRegistry;
            import javax.inject.Inject

            abstract class ShowPlugin implements Plugin<Project> {
                @Inject
                abstract SharedJavaInstallationRegistry getRegistry()

                void apply(Project project) {
                    project.tasks.register("show") {
                        println registry.listInstallations()
                    }
                }
            }

            apply plugin: ShowPlugin
        """

        when:
        result = executer
            .withEnvironmentVars([JDK1: "/unknown/env", JDK2: firstJavaHome])
            .withArgument("-Porg.gradle.java.installations.paths=/unknown/path," + secondJavaHome)
            .withArgument("-Porg.gradle.java.installations.fromEnv=JDK1,JDK2")
            .withTasks("show")
            .run()
        then:
        outputContains("${File.separator}unknown${File.separator}path' (system property 'org.gradle.java.installations.paths') used for java installations does not exist")
        outputContains("${File.separator}unknown${File.separator}env' (environment variable 'JDK1') used for java installations does not exist")
        outputContains(firstJavaHome)
        outputContains(secondJavaHome)

        when:
        result = executer
            .withEnvironmentVars([JDK1: "/unknown/env", JDK2: firstJavaHome])
            .withArgument("-Porg.gradle.java.installations.paths=/other/path," + secondJavaHome)
            .withArgument("-Porg.gradle.java.installations.fromEnv=JDK1,JDK2")
            .withTasks("show")
            .run()
        then:
        outputContains("${File.separator}other${File.separator}path' (system property 'org.gradle.java.installations.paths') used for java installations does not exist")
        outputContains("${File.separator}unknown${File.separator}env' (environment variable 'JDK1') used for java installations does not exist")
        outputContains(firstJavaHome)
        outputContains(secondJavaHome)
    }

}
