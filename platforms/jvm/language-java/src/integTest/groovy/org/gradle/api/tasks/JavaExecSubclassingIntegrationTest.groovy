/*
 * Copyright 2023 the original author or authors.
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

class JavaExecSubclassingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/24403")
    def "can use jvmArgs before super.exec()"() {
        given:
        file('src/main/java/Main.java') << """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Run app!");
                }
            }
        """

        buildFile << """
            apply plugin: 'java'

            class MyJavaExec extends JavaExec {

                @Override
                void exec() {
                    jvmArgs = jvmArgs.get() + ["-DmyProp=myValue"]
                    super.exec()
                }
            }

            task run(type: MyJavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "Main"
            }
        """

        when:
        executer.expectDeprecationWarningWithPattern("Changing property value of task ':run' property 'jvmArgs' at execution time. This behavior has been deprecated.*")
        run("run")

        then:
        outputContains("Run app!")
        outputDoesNotContain("Cannot resolve which method to invoke for [null] due to overlapping prototypes between")
    }
}
