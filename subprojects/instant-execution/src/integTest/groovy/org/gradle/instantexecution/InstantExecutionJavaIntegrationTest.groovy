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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.instantexecution.InstantExecutionBuildOperationsFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.junit.Test

class InstantExecutionJavaIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    protected InstantExecutionBuildOperationsFixture instantExecution

    def setup() {
        instantExecution = newInstantExecutionFixture()
    }

    def "build on Java project with no source"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
        """

        when:
        instantRun "build"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksNotSkipped(":jar", ":assemble", ":build")
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")

        when:
        instantRun "clean"

        then:
        instantExecution.assertStateStored()
        !file("build").exists()

        when:
        instantRun "build"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksNotSkipped(":jar", ":assemble", ":build")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")
    }

    def "build on Java project with no source and additional non-Java files in source directories"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
        """
        file("src/main/java/thing.kt") << "ignore me!"

        when:
        instantRun "build"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksNotSkipped(":jar", ":assemble", ":build")
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")

        when:
        instantRun "clean"

        then:
        instantExecution.assertStateStored()
        !file("build").exists()

        when:
        instantRun "build"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksNotSkipped(":jar", ":assemble", ":build")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")
    }

    def "build on Java project with a single source file and no dependencies"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """
        // Should be ignored by the Java source set excludes
        file("src/main/java/Gizmo.groovy") << """
            class Gizmo { def foo() {} }
        """

        when:
        instantRun "build"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class")

        when:
        instantRun "clean"

        then:
        instantExecution.assertStateStored()
        !file("build").exists()

        when:
        instantRun "build"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        classFile.isFile()
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class")
    }

    def "clean on Java project with a single source file and no dependencies"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """
        def buildDir = file("build")
        buildDir.mkdirs()

        expect:
        instantRun "clean"
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":clean")
        !buildDir.exists()

        when:
        buildDir.mkdirs()
        instantRun "clean"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":clean")
        !buildDir.exists()
    }

    def "assemble on Java project with multiple source directories"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }

            sourceSets.main.java.srcDir("src/common/java")
        """
        file("src/common/java/OtherThing.java") << """
            class OtherThing {
            }
        """
        file("src/main/java/Thing.java") << """
            class Thing extends OtherThing {
            }
        """

        when:
        instantRun "assemble"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        jarFile.isFile()

        when:
        instantRun "clean"

        then:
        instantExecution.assertStateStored()
        !file("build").exists()

        when:
        instantRun "assemble"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        classFile.isFile()
        new ZipTestFixture(jarFile).assertContainsFile("Thing.class")
        new ZipTestFixture(jarFile).assertContainsFile("OtherThing.class")
    }

    def "assemble on specific project of multi-project Java build"() {
        given:
        settingsFile << """
            include("a", "b")
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
            project(":b") {
                dependencies {
                    implementation(project(":a"))
                }
            }
        """
        file("a/src/main/java/a/A.java") << """
            package a;
            public class A {}
        """
        file("b/src/main/java/b/B.java") << """
            package b;
            public class B extends a.A {}
        """

        when:
        instantRun ":b:assemble"

        and:
        file("b/build/classes/java/main/b/B.class").delete()
        def jarFile = file("b/build/libs/b.jar")
        jarFile.isFile()

        and:
        instantRun "clean"

        and:
        instantRun ":b:assemble"

        then:
        new ZipTestFixture(jarFile).assertContainsFile("b/B.class")
    }

    def "processResources on Java project honors up-to-date check"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """
        file("src/main/resources/answer.txt") << "42"

        when:
        instantRun ":processResources"

        and:
        instantRun ":processResources"

        then:
        file("build/resources/main/answer.txt").text == "42"
        result.assertTasksSkipped(":processResources")
        instantExecution.assertStateLoaded()
    }

    def "processResources on Java project honors task inputs"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """
        file("src/main/resources/answer.txt") << "42"

        when:
        instantRun ":processResources"

        and:
        file("build").deleteDir()

        and:
        instantRun ":processResources"

        then:
        instantExecution.assertStateLoaded()
        file("build/resources/main/answer.txt").text == "42"

        when: "task input changes"
        file("src/main/resources/answer.txt").text = "forty-two"

        and:
        instantRun ":processResources"

        then:
        instantExecution.assertStateLoaded()
        file("build/resources/main/answer.txt").text == "forty-two"
    }

    def "processResources on Java project honors task outputs"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """
        file("src/main/resources/answer.txt") << "42"

        when:
        instantRun ":processResources"

        and:
        file("build/resources/main/answer.txt").text == "forty-two"

        and:
        instantRun ":processResources"

        then:
        instantExecution.assertStateLoaded()
        file("build/resources/main/answer.txt").text == "42"
    }

    def "build on Java project with JUnit tests"() {
        given:
        settingsFile << """
            rootProject.name = 'somelib'
        """
        buildFile << """
            plugins { id 'java' }
            ${jcenterRepository()}
            dependencies { testImplementation "junit:junit:4.12" }
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """
        file("src/test/java/ThingTest.java") << """
            import ${Test.name};

            public class ThingTest {
                @Test
                public void ok() {
                    new Thing();
                }
            }
        """

        when:
        instantRun "build"

        then:
        instantExecution.assertStateStored()
        this.result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def testClassFile = file("build/classes/java/test/ThingTest.class")
        testClassFile.isFile()
        def testResults = file("build/test-results/test")
        testResults.isDirectory()
        assertTestsExecuted("ThingTest", "ok")

        when:
        instantRun "clean"

        then:
        instantExecution.assertStateStored()
        !file("build").exists()

        when:
        instantRun "build"

        then:
        instantExecution.assertStateLoaded()
        this.result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()
        assertTestsExecuted("ThingTest", "ok")
    }

    def "build on Java application project with no dependencies"() {
        given:
        settingsFile << """
            rootProject.name = 'someapp'
        """
        buildFile << """
            plugins { id 'application' }
            application.mainClassName = 'Thing'
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """

        when:
        instantRun "build"

        then:
        instantExecution.assertStateStored()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":startScripts", ":distTar", ":distZip", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/someapp.jar")
        jarFile.isFile()

        when:
        instantRun "clean"

        then:
        instantExecution.assertStateStored()
        !file("build").exists()

        when:
        instantRun "build"

        then:
        instantExecution.assertStateLoaded()
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":startScripts", ":distTar", ":distZip", ":assemble", ":check", ":build")
        classFile.isFile()
        jarFile.isFile()
    }
}
