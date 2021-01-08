/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import spock.lang.Issue

/**
 * Tests that task classes compiled against earlier versions of Gradle using the static Groovy compiler are still compatible.
 *
 * <p>Note: Groovy introduced static compilation ({@link groovy.transform.CompileStatic}) in Groovy 2.0.0.
 * We switched to using Groovy 2.3.3 from 1.8.6 in Gradle 2.0. However, Groovy 2.3.3 shipped with Gradle 2.0 had a bug that prevents the test to be compiled.
 * Thus the first version we test with is Gradle 2.1 that shipped with Groovy 2.3.6 which fixed that issue.
 *
 * <b>Update for 7.0:</b> the FileOperations interface was finally removed from `DefaultProject`. Unfortunately that
 * broke compatibility with plugins compiled with Gradle 4.x. And that's why the first version we test with now is
 * Gradle 5.0.
 */
@TargetVersions("5.0+")
class StaticGroovyTaskSubclassingBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/6027")
    def "task can use project.file() from statically typed Groovy"() {
        when:
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                compile localGroovy()
                compile gradleApi()
            }
        """

        file("producer/src/main/groovy/SubclassTask.groovy") << """
            import groovy.transform.CompileStatic
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            @CompileStatic
            class SubclassTask extends DefaultTask {
                @TaskAction
                void doGet() {
                    project.file("file.txt")
                }
            }
        """

        buildFile << """
            buildscript {
                dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
            }

            task task(type: SubclassTask)
        """

        then:
        version previous withTasks 'assemble' inDirectory(file("producer")) run()
        version current requireDaemon() requireIsolatedDaemons() withTasks 'task' run()
    }
}
