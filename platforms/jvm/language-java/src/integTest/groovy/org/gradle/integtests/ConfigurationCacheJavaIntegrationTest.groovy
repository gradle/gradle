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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Test

import static org.hamcrest.CoreMatchers.containsString

@Requires(value = IntegTestPreconditions.NotConfigCached, reason = "handles CC explicitly")
class ConfigurationCacheJavaIntegrationTest extends AbstractIntegrationSpec {

    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
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
        run "build"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":jar", ":assemble", ":build")
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything is up-to-date
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")

        when:
        run "clean"

        then:
        assertStateStored()
        !file("build").exists()

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":jar", ":assemble", ":build")
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
        // Should be ignored by the Java source set excludes
        file("src/main/java/thing.kt") << "class Thing(val name: String)"
        file("src/main/java/Gizmo.groovy") << """
            class Gizmo { def foo() {} }
        """

        when:
        run "build"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":jar", ":assemble", ":build")
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")

        when:
        run "clean"

        then:
        assertStateStored()
        !file("build").exists()

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":jar", ":assemble", ":build")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF")
    }

    def "build on Java project with a single source file and no dependencies"() {
        given:
        buildWithSingleSourceFile()

        when:
        run "build"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class")

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything should be up-to-date
        classFile.isFile()
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class")

        when:
        run "clean"

        then:
        assertStateStored()
        !file("build").exists()

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":compileJava", ":classes", ":jar", ":assemble", ":build")
        classFile.isFile()
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class")

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything should be up-to-date
        classFile.isFile()
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class")

        when:
        file("src/main/java/thing/NewThing.java") << """
            package thing;

            public class NewThing { }
        """
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":compileJava", ":classes", ":jar", ":assemble", ":build")
        classFile.isFile()
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class", "thing/NewThing.class")
    }

    def "clean on Java project with a single source file and no dependencies"() {
        given:
        buildWithSingleSourceFile()

        def buildDir = file("build")
        buildDir.mkdirs()

        expect:
        run "clean"
        assertStateStored()
        result.assertTasksScheduled(":clean")
        !buildDir.exists()

        when:
        buildDir.mkdirs()
        run "clean"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":clean")
        !buildDir.exists()
    }

    def "build on Java project with source files and resources and no dependencies"() {
        given:
        buildWithSourceFilesAndResources()

        when:
        run "build"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class", "answer.txt", "META-INF/some.Service")

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything should be up-to-date
        classFile.isFile()
        new ZipTestFixture(jarFile).hasDescendants("META-INF/MANIFEST.MF", "Thing.class", "answer.txt", "META-INF/some.Service")

        when:
        run "clean"

        then:
        assertStateStored()
        !file("build").exists()

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":jar", ":assemble", ":build")
        classFile.isFile()
        new ZipTestFixture(jarFile).with {
            hasDescendants("META-INF/MANIFEST.MF", "Thing.class", "answer.txt", "META-INF/some.Service")
            assertFileContent("answer.txt", "42")
        }

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything should be up-to-date
        classFile.isFile()
        new ZipTestFixture(jarFile).with {
            hasDescendants("META-INF/MANIFEST.MF", "Thing.class", "answer.txt", "META-INF/some.Service")
            assertFileContent("answer.txt", "42")
        }

        when:
        file("src/main/resources/answer.txt").text = "forty-two"
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":processResources", ":classes", ":jar", ":assemble", ":build")
        classFile.isFile()
        new ZipTestFixture(jarFile).with {
            hasDescendants("META-INF/MANIFEST.MF", "Thing.class", "answer.txt", "META-INF/some.Service")
            assertFileContent("answer.txt", "forty-two")
        }
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
        run "assemble"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/somelib.jar")
        jarFile.isFile()

        when:
        run "clean"

        then:
        assertStateStored()
        // CC report is still generated since the build script looks up the property `sourceSets`
        file("build").list().toList() == ['reports']

        when:
        run "assemble"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":assemble")
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
        run ":b:assemble"

        and:
        file("b/build/classes/java/main/b/B.class").delete()
        def jarFile = file("b/build/libs/b.jar")
        jarFile.isFile()

        and:
        run "clean"

        and:
        run ":b:assemble"

        then:
        new ZipTestFixture(jarFile).assertContainsFile("b/B.class")
    }

    def "processResources on Java project honors task outputs"() {
        given:
        buildFile << """
            apply plugin: 'java'
        """
        file("src/main/resources/answer.txt") << "42"

        when:
        run ":processResources"

        and:
        file("build/resources/main/answer.txt").text == "forty-two"

        and:
        run ":processResources"

        then:
        assertStateLoaded()
        file("build/resources/main/answer.txt").text == "42"
    }

    def "build on Java project with JUnit tests"() {
        given:
        buildWithSingleSourceFile()
        buildFile << """
            ${mavenCentralRepository()}
            dependencies { testImplementation "junit:junit:4.13" }
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
        run "build"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def testClassFile = file("build/classes/java/test/ThingTest.class")
        testClassFile.isFile()
        def testResults = file("build/test-results/test")
        testResults.isDirectory()
        assertTestsExecuted("ThingTest", "ok")

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything should be up-to-date
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()
        assertTestsExecuted("ThingTest", "ok")

        when:
        run "clean"

        then:
        assertStateStored()
        !file("build").exists()

        when:
        run "build"

        then:
        assertStateLoaded()
        this.result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":assemble", ":check", ":build")
        result.assertTasksSkipped(":processResources", ":processTestResources")
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
            application.mainClass = 'Thing'
        """
        file("src/main/java/Thing.java") << """
            class Thing {
            }
        """

        when:
        run "build"

        then:
        assertStateStored()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":startScripts", ":distTar", ":distZip", ":assemble", ":check", ":build")
        def classFile = file("build/classes/java/main/Thing.class")
        classFile.isFile()
        def jarFile = file("build/libs/someapp.jar")
        jarFile.isFile()

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":startScripts", ":distTar", ":distZip", ":assemble", ":check", ":build")
        result.assertAllTasksSkipped() // everything should be up-to-date
        classFile.isFile()
        jarFile.isFile()

        when:
        run "clean"

        then:
        assertStateStored()
        // CC report is still generated since the build script looks up the property `application`
        file("build").list().toList() == ['reports']

        when:
        run "build"

        then:
        assertStateLoaded()
        result.assertTasksScheduled(":compileJava", ":processResources", ":classes", ":jar", ":compileTestJava", ":processTestResources", ":testClasses", ":test", ":startScripts", ":distTar", ":distZip", ":assemble", ":check", ":build")
        result.assertTasksExecuted(":compileJava", ":classes", ":jar", ":startScripts", ":distTar", ":distZip", ":assemble", ":build")
        classFile.isFile()
        jarFile.isFile()
    }

    def "jar manifest honours build logic inputs"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            jar {
                manifest {
                    attributes('Created-By': providers.systemProperty("creator"))
                }
            }
        """
        def manifestFile = file("build/tmp/jar/MANIFEST.MF")
        def assertCreatedBy = { creator ->
            manifestFile.assertContents(
                containsString("Created-By: $creator")
            )
        }

        when:
        run ":jar", "-Dcreator=creator1"

        then:
        assertCreatedBy 'creator1'
        assertStateStored()

        when:
        run ":jar", "-Dcreator=creator2"

        then:
        assertCreatedBy 'creator2'
        assertStateLoaded()

        when:
        manifestFile.delete()
        run ":jar", "-Dcreator=creator3"

        then:
        assertCreatedBy 'creator3'
        assertStateLoaded()
    }

    def "jar manifest can use mapped FileCollection elements"() {
        given:
        settingsFile << '''
            include 'lib'
            include 'app'
        '''
        file('lib/build.gradle') << '''
            plugins { id("java-library") }
        '''
        file('app/build.gradle.kts') << '''
            plugins {
                `java-library`
            }

            val manifestClasspath by configurations.creating {
                assert(isCanBeResolved)
                isCanBeConsumed = false
                isTransitive = false
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
                }
            }

            tasks.jar.configure {
                inputs.files(manifestClasspath)
                val classpath = manifestClasspath.elements.map { classpathDependency ->
                    classpathDependency.joinToString(" ") {
                        it.asFile.name
                    }
                }
                manifest.attributes("Class-Path" to classpath)
            }

            dependencies {
                implementation(project(":lib"))
                manifestClasspath(project(":lib"))
            }
        '''
        file('lib/src/main/java/lib/Utils.java') << '''
            package lib;
            public class Utils {}
        '''
        file('app/src/main/java/launcher/Main.java') << '''
            package launcher;
            public class Main {}
        '''

        when:
        run ':app:jar'

        then:
        assertStateStored()

        when:
        def jarFile = file('app/build/libs/app.jar')
        jarFile.delete()

        and:
        run ':app:jar'

        then:
        assertStateLoaded()

        and:
        def manifest = new JarTestFixture(jarFile).manifest
        manifest.mainAttributes.getValue('Class-Path') == 'lib.jar'
    }

    def buildWithSingleSourceFile() {
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
    }

    def buildWithSourceFilesAndResources() {
        buildWithSingleSourceFile()

        file("src/main/resources/answer.txt") << "42"
        file("src/main/resources/META-INF/some.Service") << "impl"
    }

    private void assertStateStored() {
        configurationCache.assertStateStored()
    }

    private void assertStateLoaded() {
        configurationCache.assertStateLoaded()
    }

    protected void assertTestsExecuted(String testClass, String... testNames) {
        new DefaultTestExecutionResult(testDirectory)
            .testClass(testClass)
            .assertTestsExecuted(testNames)
    }
}
