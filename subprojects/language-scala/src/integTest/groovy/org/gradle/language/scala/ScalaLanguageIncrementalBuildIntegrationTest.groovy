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

package org.gradle.language.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.scala.fixtures.TestScalaLibrary
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile

class ScalaLanguageIncrementalBuildIntegrationTest extends AbstractIntegrationSpec {
    def app = new TestScalaLibrary()
    List<TestFile> sourceFiles
    List<TestFile> resourceFiles

    def setup() {
        sourceFiles = app.sources*.writeToDir(file("src/main/scala"))
        resourceFiles = app.resources*.writeToDir(file("src/main/resources"))

        buildFile << """
    plugins {
        id 'jvm-component'
        id 'scala-lang'
    }

    repositories{
        mavenCentral()
    }

    model {
        components {
            main(JvmLibrarySpec)
        }
    }
"""
    }

    def "builds jar"() {
        when:
        run "mainJar"

        then:
        executedAndNotSkipped ":compileMainJarMainScala", ":processMainJarMainResources", ":createMainJar", ":mainJar"

        and:
        jarFile("build/jars/mainJar/main.jar").hasDescendants(app.expectedOutputs*.fullPath as String[])
    }

    def "does not re-execute build with no change"() {
        given:
        run "mainJar"

        when:
        run "mainJar"

        then:
        nonSkippedTasks.empty
    }

    def "rebuilds jar and classfile is removed when source file removed"() {
        given:
        run "mainJar"

        when:
        sourceFiles[1].delete()
        run "mainJar"

        then:
        executedAndNotSkipped ":compileMainJarMainScala", ":createMainJar", ":mainJar"

        and:
        String[] expectedClasses = [app.sources[0].classFile.fullPath, app.resources[0].fullPath, app.resources[1].fullPath]
        file("build/classes/mainJar").assertHasDescendants(expectedClasses)
        jarFile("build/jars/mainJar/main.jar").hasDescendants(expectedClasses)
    }

    def "rebuilds jar without resource when resource removed"() {
        given:
        run "mainJar"

        when:
        resourceFiles[1].delete()
        run "mainJar"

        then:
        executedAndNotSkipped ":processMainJarMainResources", ":createMainJar", ":mainJar"

        and:
        String[] expectedClasses = [app.sources[0].classFile.fullPath, app.sources[1].classFile.fullPath, app.resources[0].fullPath]
        file("build/classes/mainJar").assertHasDescendants(expectedClasses)
        jarFile("build/jars/mainJar/main.jar").hasDescendants(expectedClasses)
    }

    def "rebuilds jar when source file changed"() {
        given:
        run "mainJar"

        when:
        sourceFiles[0].text = sourceFiles[0].text.replace("name", "lastName")
        run "mainJar"

        then:
        executedAndNotSkipped ":compileMainJarMainScala", ":createMainJar", ":mainJar"
    }

    def "rebuilds jar when resource file changed"() {
        given:
        run "mainJar"

        when:
        resourceFiles[0].text = "Some different text"
        run "mainJar"

        then:
        executedAndNotSkipped ":processMainJarMainResources", ":createMainJar", ":mainJar"
    }

    def "rebuilds jar when source file added"() {
        given:
        run "mainJar"

        when:
        file("src/main/scala/Extra.java") << """
interface Extra {
    String whatever();
}
"""
        run "mainJar"

        then:
        executedAndNotSkipped ":compileMainJarMainScala", ":createMainJar", ":mainJar"

        and:
        file("build/classes/mainJar/Extra.class").assertExists()
        jarFile("build/jars/mainJar/main.jar").assertContainsFile("Extra.class")
    }

    def "rebuilds jar when resource file added"() {
        given:
        run "mainJar"

        when:
        file("src/main/resources/Extra.txt") << "an extra resource"
        run "mainJar"

        then:
        executedAndNotSkipped ":processMainJarMainResources", ":createMainJar", ":mainJar"

        and:
        file("build/classes/mainJar/Extra.txt").assertExists()
        jarFile("build/jars/mainJar/main.jar").assertContainsFile("Extra.txt")
    }

    def "recompiles but does not rebuild jar when source file changed such that bytecode is the same"() {
        given:
        run "mainJar"

        when:
        sourceFiles[0].text = sourceFiles[0].text.replace("age;", "age; // Line trailing comment")
        run "mainJar"

        then:
        executedAndNotSkipped ":compileMainJarMainScala"
        skipped ":createMainJar", ":mainJar"
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }
}