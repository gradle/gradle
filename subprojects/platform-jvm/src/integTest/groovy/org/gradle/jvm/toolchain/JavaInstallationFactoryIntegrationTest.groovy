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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.internal.jvm.Jvm
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf

import javax.inject.Inject

class JavaInstallationFactoryIntegrationTest extends AbstractIntegrationSpec {
    def "plugin can query information about the current JVM"() {
        buildFile << """
            import ${Inject.name}

            abstract class QueryTask extends DefaultTask {
                @Inject
                abstract JavaInstallationRegistry getFactory()
                
                @TaskAction
                def show() {
                    def javaInstallation = factory.thisVirtualMachine
                    println("java home = \${javaInstallation.javaHome}")
                    println("java version = \${javaInstallation.javaVersion}")
                }
            }
            
            task show(type: QueryTask)
        """

        when:
        run("show")

        then:
        outputContains("java home = ${Jvm.current().javaHome}")
        outputContains("java version = ${Jvm.current().javaVersion}")
    }

    @IgnoreIf({ AvailableJavaHomes.differentVersion == null })
    def "can query information about another JVM"() {
        def jvm = AvailableJavaHomes.differentVersion
        buildFile << """
            import ${Inject.name}

            abstract class QueryTask extends DefaultTask {
                @Inject
                abstract JavaInstallationRegistry getFactory()
                
                @TaskAction
                def show() {
                    def javaInstallation = factory.forDirectory(project.file("${jvm.javaHome.toURI()}")).get()
                    println("java home = \${javaInstallation.javaHome}")
                    println("java version = \${javaInstallation.javaVersion}")
                }
            }
            
            task show(type: QueryTask)
        """

        when:
        run("show")

        then:
        outputContains("java home = ${jvm.javaHome}")
        outputContains("java version = ${jvm.javaVersion}")
    }

    @IgnoreIf({ AvailableJavaHomes.differentVersion == null })
    @Requires(TestPrecondition.SYMLINKS)
    @ToBeFixedForInstantExecution
    def "notices changes to Java installation between builds"() {
        def jvm = AvailableJavaHomes.differentVersion

        buildFile << """
            import ${Inject.name}

            abstract class QueryTask extends DefaultTask {
                @Inject
                abstract JavaInstallationRegistry getFactory()
                
                @TaskAction
                def show() {
                    def javaInstallation = factory.forDirectory(project.file("install")).get()
                    println("java home = \${javaInstallation.javaHome}")
                    println("java version = \${javaInstallation.javaVersion}")
                }
            }
            
            task show(type: QueryTask)
        """

        def javaHome = file("install")
        javaHome.createLink(jvm.javaHome)

        when:
        run("show")

        then:
        outputContains("java home = ${jvm.javaHome}")
        outputContains("java version = ${jvm.javaVersion}")

        when:
        javaHome.createLink(Jvm.current().javaHome)
        run("show")

        then:
        outputContains("java home = ${Jvm.current().javaHome}")
        outputContains("java version = ${Jvm.current().javaVersion}")
    }

    def "reports unrecognized Java installation"() {
        file("install/bin/java").createFile()

        buildFile << """
            import ${Inject.name}

            abstract class QueryTask extends DefaultTask {
                @Inject
                abstract JavaInstallationRegistry getFactory()
                
                @TaskAction
                def show() {
                    factory.forDirectory(project.file("install")).get()
                }
            }
            
            task show(type: QueryTask)
        """

        when:
        fails("show")

        then:
        // TODO - improve the error message for common failures
        failure.assertHasDescription("Execution failed for task ':show'.")
        failure.assertHasCause("Could not determine the details of Java installation in directory ${file("install")}.")
    }
}
