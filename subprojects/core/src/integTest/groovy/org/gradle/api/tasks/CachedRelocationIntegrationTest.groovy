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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.LocalBuildCacheFixture

class CachedRelocationIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    def "relocating the project doesn't invalidate custom tasks declared in build script"() {
        def originalLocation = file("original-location").createDir()

        originalLocation.file("external.gradle").text = externalTaskDef()
        originalLocation.file("input.txt") << "input"
        originalLocation.file("input-2.txt") << "input-2"
        originalLocation.file("src/main/java/Hello.java") << """
            public class Hello {
                public static void main(String... args) {
                    System.out.println("Hello World!");
                }
            }
        """
        originalLocation.file("build.gradle") << """
            println "Running build from: \$projectDir"
            apply plugin: "java"
            apply from: "external.gradle"
        """

        when:
        executer.usingProjectDirectory(originalLocation)
        withBuildCache().succeeds "jar", "customTask"

        then:
        nonSkippedTasks.containsAll ":compileJava", ":jar", ":customTask"

        when:
        executer.usingProjectDirectory(originalLocation)
        originalLocation.file("external.gradle").text = externalTaskDef("modified")
        withBuildCache().succeeds "jar", "customTask"

        then:
        skippedTasks.containsAll ":compileJava"
        nonSkippedTasks.contains ":customTask"

        when:
        executer.usingProjectDirectory(originalLocation)
        run "clean"

        executer.usingProjectDirectory(originalLocation)
        withBuildCache().succeeds "jar", "customTask"

        then:
        skippedTasks.containsAll ":compileJava", ":customTask"

        when:
        def movedLocation = temporaryFolder.file("moved-location")
        originalLocation.renameTo(movedLocation)
        movedLocation.file("build").deleteDir()
        movedLocation.file(".gradle").deleteDir()

        executer.usingProjectDirectory(movedLocation)
        withBuildCache().succeeds "jar", "customTask"

        then:
        // Built-in tasks are loaded from cache
        skippedTasks.containsAll ":compileJava"
        // Custom tasks are also loaded from cache
        skippedTasks.contains ":customTask"
    }

    static String externalTaskDef(String suffix = "") {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                File inputFile

                @OutputFile File outputFile

                @TaskAction void doSomething() {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = inputFile.text + '$suffix'
                }
            }

            task customTask(type: CustomTask) {
                inputFile = file "input.txt"
                outputFile = file "build/output.txt"
            }
        """
    }
}
