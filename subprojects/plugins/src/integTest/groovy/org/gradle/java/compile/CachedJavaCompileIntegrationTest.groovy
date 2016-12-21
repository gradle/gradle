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

package org.gradle.java.compile

import org.gradle.AbstractCachedCompileIntegrationTest
import org.gradle.test.fixtures.file.TestFile

class CachedJavaCompileIntegrationTest extends AbstractCachedCompileIntegrationTest {
    String compilationTask = ':compileJava'
    String compiledFile = "build/classes/main/Hello.class"

    def setupProjectInDirectory(TestFile project = temporaryFolder.testDirectory) {
        project.with {
            file('build.gradle').text = """
            plugins {
                id 'java'
                id 'application'
            }

            mainClassName = "Hello"

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.codehaus.groovy:groovy-all:2.4.7'
            }
        """.stripIndent()

        file('src/main/java/Hello.java') << """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello!");
                }
            }
        """.stripIndent()
        }
    }

    def "up-to-date incremental compilation is cached if nothing to recompile"() {
        given: 'a JavaCompile task with incremental compilation enabled and inputs not participating in actual compilation'
        buildFile << '''
            tasks.withType(org.gradle.api.tasks.compile.AbstractCompile) { task ->
                task.options.incremental = true
                task.inputs.file 'input.txt'
            }
        '''.stripIndent()
        file('input.txt') << 'original content'

        when: 'first run'
        withBuildCache().succeeds compilationTask

        then: 'task is EXECUTED'
        executedAndNotSkipped compilationTask
        compileIsNotCached()

        when: 'changing input not participating in actual compilation'
        file('input.txt') << 'triggers task execution, but no recompilation is necessary'

        and: 'second run'
        withBuildCache().succeeds compilationTask, '--debug'

        then: 'incremental compilation is executed but outcome is UP-TO-DATE'
        result.output.contains "Executing actions for task '${compilationTask}'."
        result.output.contains "${compilationTask} UP-TO-DATE"
        executed compilationTask
        // TODO Fails but shouldn't
        // It seems that the notion of "skipped tasks" is flawed
        // It should distinguish UP-TO-DATE and FROM-CACHE to allow us to assert things correctly
        // skipped compilationTask

        and: 'compilation task output is not FROM-CACHE'
        // Same issue
        // compileIsNotCached()

        when: 'clean then third run'
        withBuildCache().succeeds 'clean', 'run'

        then: 'compilation is FROM-CACHE'
        compileIsCached()
    }
}
