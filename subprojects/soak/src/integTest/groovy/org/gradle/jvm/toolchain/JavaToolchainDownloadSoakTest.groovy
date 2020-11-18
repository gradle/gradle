/*
 * Copyright 2014 the original author or authors.
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

class JavaToolchainDownloadSoakTest extends AbstractIntegrationSpec {

    def "can download missing jdk automatically"() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        result = executer
            .withArguments("--info", "-Porg.gradle.java.installations.auto-detect=false")
            .withTasks("compileJava")
            .withToolchainDownloadEnabled()
            .requireOwnGradleUserHomeDir()
            .run()

        then:
        outputContains("Compiling with toolchain '${executer.gradleUserHomeDir.absolutePath}${File.separator}jdks${File.separator}jdk-14.")
        javaClassFile("Foo.class").exists()
    }

    def "can download missing j9 jdk automatically"() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                    implementation = JvmImplementation.J9
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        result = executer
            .withArguments("--info", "-Porg.gradle.java.installations.auto-detect=false")
            .withTasks("compileJava")
            .withToolchainDownloadEnabled()
            .requireOwnGradleUserHomeDir()
            .run()

        then:
        outputContains("Compiling with toolchain '${executer.gradleUserHomeDir.absolutePath}${File.separator}jdks${File.separator}jdk-14.")
        javaClassFile("Foo.class").exists()
    }

}
