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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class JavaCompileIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-3152")
    def "can use the task without applying java-base plugin"() {
        buildFile << """
            task compile(type: JavaCompile) {
                classpath = files()
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                dependencyCacheDir = file("build/dependency-cache")
                source "src/main/java"
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        run("compile")

        then:
        file("build/classes/Foo.class").exists()
    }

    def "uses default platform settings when applying java plugin"() {
        buildFile << """
            apply plugin:"java"
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        run("compileJava")
        then:
        file("build/classes/main/Foo.class").exists()
    }

    def "don't implicitly compile source files from classpath"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            subprojects {
                apply plugin: 'java'
                tasks.withType(JavaCompile) {
                    options.compilerArgs << '-Xlint:all' << '-Werror'
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
            }
"""

        file("a/src/main/resources/Foo.java") << "public class Foo {}"

        file("b/src/main/java/Bar.java") << "public class Bar extends Foo {}"

        expect:
        fails("compileJava")
        failure.assertHasDescription("Execution failed for task ':b:compileJava'.")

        // This makes sure the test above is correct AND you can get back javac's default behavior if needed
        when:
        buildFile << "project(':b').compileJava { options.sourcepath = classpath }"
        run("compileJava")
        then:
        file("b/build/classes/main/Bar.class").exists()
        file("b/build/classes/main/Foo.class").exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3508")
    def "detects change in classpath order"() {
        file("lib1.jar") << jarWithContents("data.txt": "data1")
        file("lib2.jar") << jarWithContents("data.txt": "data2")
        file("src/main/java/Foo.java") << "public class Foo {}"

        buildFile << buildScriptWithClasspath("lib1.jar", "lib2.jar")

        when:
        run "compile"
        then:
        nonSkippedTasks.contains ":compile"

        when:
        run "compile"
        then:
        skippedTasks.contains ":compile"

        // Need to wait for build script cache to be able to recognize change
        sleep(1000L)

        buildFile.delete()
        buildFile << buildScriptWithClasspath("lib2.jar", "lib1.jar")

        when:
        run "compile"
        then:
        nonSkippedTasks.contains ":compile"
    }

    def buildScriptWithClasspath(String... dependencies) {
        """
            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                dependencyCacheDir = file("build/dependency-cache")
                source "src/main/java"
                classpath = files('${dependencies.join("', '")}')
            }
        """
    }

    def jarWithContents(Map<String, String> contents) {
        def out = new ByteArrayOutputStream()
        def jarOut = new JarOutputStream(out)
        try {
            contents.each { file, fileContents ->
                def zipEntry = new ZipEntry(file)
                zipEntry.setTime(0)
                jarOut.putNextEntry(zipEntry)
                jarOut << fileContents
            }
        } finally {
            jarOut.close()
        }
        return out.toByteArray()
    }
}
