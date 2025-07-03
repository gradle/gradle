/*
 * Copyright 2025 the original author or authors.
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
import spock.lang.Issue

class ExecKotlinDSLIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/33861")
    def "can configure Exec.args using #method"() {
        given:
        file("src/main/java/Echo.java") << echoClass
        buildKotlinFile << """
            import org.gradle.internal.jvm.Jvm

            plugins {
                id("java")
            }

            tasks.register<Exec>("execTask") {
                dependsOn(sourceSets["main"].runtimeClasspath)
                executable = Jvm.current().getJavaExecutable().absolutePath
                ${expression}
            }
        """

        when:
        run "execTask"

        then:
        outputContains("foo+bar")

        where:
        method          | expression
        "a property"    | "args = listOf(${echoArguments})"
        "its setter"    | "setArgs(listOf(${echoArguments}))"
    }

    def "can execute command without setting any value for Exec.args"() {
        buildKotlinFile << """
            import org.gradle.internal.jvm.Jvm

            tasks.register<Exec>("execTask") {
                executable = Jvm.current().getJavaExecutable().absolutePath
                setIgnoreExitValue(true)
            }
        """

        when:
        run "execTask"

        then:
        errorOutput.contains("Usage: java")
    }

    String getEchoArguments() {
        return """ "-cp", sourceSets["main"].runtimeClasspath.asPath, "Echo", "foo", "bar" """
    }

    String getEchoClass() {
        return """
            class Echo {
                public static void main(String[] args) {
                    System.out.println(String.join("+", args));
                }
            }
        """
    }
}
