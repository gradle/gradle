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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class CachedRelocationIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def "relocating the project doesn't invalidate custom tasks declared in build script"() {
        def originalLocation = file("original-location").createDir()
        def originalHome = file("original-home").createDir()

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
            println "Running in Gradle home: \${gradle.gradleUserHomeDir}"

            apply plugin: "java"
            apply from: "external.gradle"
        """
        originalLocation.file('settings.gradle') << localCacheConfiguration()

        when:
        executer.usingProjectDirectory(originalLocation)
        executer.withGradleUserHomeDir(originalHome)
        withBuildCache().run "jar", "customTask"

        then:
        executedAndNotSkipped ":compileJava", ":jar", ":customTask"

        when:
        executer.usingProjectDirectory(originalLocation)
        originalLocation.file("external.gradle").text = externalTaskDef("modified")
        withBuildCache().run "jar", "customTask"

        then:
        skipped ":compileJava"
        executedAndNotSkipped ":customTask"

        when:
        executer.usingProjectDirectory(originalLocation)
        run "clean"

        def movedHome = temporaryFolder.file("moved-home")
        executer.withGradleUserHomeDir(movedHome)
        executer.usingProjectDirectory(originalLocation)
        withBuildCache().run "jar", "customTask"

        then:
        skipped ":compileJava", ":customTask"

        when:
        executer.usingProjectDirectory(originalLocation)
        run "clean"

        executer.usingProjectDirectory(originalLocation)
        withBuildCache().run "jar", "customTask"

        then:
        skipped ":compileJava", ":customTask"

        when:
        def movedLocation = temporaryFolder.file("moved-location")
        originalLocation.renameTo(movedLocation)
        movedLocation.file("build").deleteDir()
        movedLocation.file(".gradle").deleteDir()

        executer.usingProjectDirectory(movedLocation)
        withBuildCache().run "jar", "customTask"

        then:
        // Built-in tasks are loaded from cache
        skipped ":compileJava"
        // Custom tasks are also loaded from cache
        skipped ":customTask"
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
                doFirst {
                    printScriptOrigin("Action", owner)
                }
            }

            def printScriptOrigin(def title, def o) {
                // need to get through reflection to bypass the Groovy MOP on closures, which would cause calling the method on the owner instead of the closure itself
                def type = o.getClass()
                def originalClassName = type.getMethod('getOriginalClassName').invoke(o)
                def contentHash = type.getMethod('getContentHash').invoke(o)
                println "\${title} class name: \${originalClassName} (remapped: \${type.name})"
                println "\${title} class hash: \${contentHash}"
            }
        """
    }
}
