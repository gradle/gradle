/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

// NB: there's nothing specific about Java support and continuous.
//     this spec just lays out some more practical use cases than the other targeted tests.
class SimpleJavaContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java'
        """
        executer.withStackTraceChecksDisabled() // some tests fail compilation
    }

    def "can build when no source dir present"() {
        when:
        assert !file("src/main/java").exists()

        then:
        succeeds("build")
        ":compileJava" in skippedTasks
        ":build" in executedTasks
    }

    def "can build when source dir is removed"() {
        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"

        when:
        assert !file("src/main/java").deleteDir().exists()

        then:
        succeeds()
        ":compileJava" in skippedTasks
        ":build" in executedTasks
    }

    def "build is not triggered when a new directory is created in the source inputs"() {
        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds("build")

        when:
        file("src/main/java/foo").createDir()

        then:
        noBuildTriggered()
    }

    def "after compilation failure, fixing file retriggers build"() {
        when:
        def sourceFile = file("src/main/java/Thing.java") << "class Thing {} /* broken compile "

        then:
        fails("build")

        when:
        sourceFile << "*/"

        then:
        succeeds()
    }

    def "can run tests"() {
        when:
        def sourceFile = file("src/main/java/Thing.java") << "class Thing {}"
        def testFile = file("src/test/java/TestThing.java") << "class TestThing {}"
        def resourceFile = file("src/main/resources/thing.text") << "thing"

        then:
        succeeds("test")
        executedAndNotSkipped(":compileJava", ":processResources", ":compileTestJava", ":test")

        when:
        sourceFile.text = "class Thing { static public int FLAG = 1; }"

        then:
        succeeds()
        executedAndNotSkipped(":compileJava", ":compileTestJava", ":test")
        skipped(":processResources")

        when:
        testFile.text = "class TestThing { static public int FLAG = 1; }"

        then:
        succeeds()
        executedAndNotSkipped(":compileTestJava", ":test")
        skipped(":processResources", ":compileJava")

        when:
        resourceFile << "# another change"

        then:
        succeeds()
        executedAndNotSkipped(":processResources", ":compileTestJava", ":test")
        skipped(":compileJava")
    }

    def "failing main source build ignores changes to test source"() {
        when:
        def sourceFile = file("src/main/java/Thing.java") << "class Thing {}"
        def testSourceFile = file("src/test/java/ThingTest.java") << "class ThingTest {}"

        then:
        succeeds("test")

        when:
        testSourceFile << " broken "

        then:
        fails()
        failureDescriptionStartsWith("Execution failed for task ':compileTestJava'.")

        when:
        sourceFile << " broken "

        then:
        fails()
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")

        when:
        testSourceFile.text = "class ThingTest {}"

        then:
        noBuildTriggered()

        when:
        sourceFile.text = "class Thing {}"

        then:
        succeeds()
    }

    // Just exercises the dependency management layers to shake out any weirdness
    def "can resolve dependencies from remote repository"() {
        when:
        def sourceFile = file("src/main/java/Thing.java") << "class Thing {}"

        buildFile << """
            repositories {
                mavenCentral()
            }
            dependencies {
                compile "log4j:log4j:1.2.17"
            }
        """

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava"

        when:
        sourceFile.text = "import org.apache.log4j.LogManager; class Thing {}"

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "dependencies as inputs from local filesystem"() {
        when:
        def somelib = file("lib/somelib.jar")
        somelib.parentFile.mkdir()
        file("src/main/java/Thing.java") << "class Thing {}"
        buildFile << """
            dependencies {
                compile files("lib/somelib.jar")
            }
        """

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"

        when:
        createJar(somelib, "META-INF/")

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"

        when:
        createJar(somelib, "META-INF/", "another-dir/")

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"

        when:
        somelib.delete()

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "multiple dependencies as inputs from local filesystem"() {
        when:
        def libDir = file('libs').createDir()
        createJar(libDir.file("somelib.jar"), "META-INF/")
        file("src/main/java/Thing.java") << "class Thing {}"
        buildFile << """
            dependencies {
                compile fileTree("libs/")
            }
        """

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"

        when:
        createJar(libDir.file("anotherlib.jar"), "META-INF/")

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "creation of initial source file triggers build"() {
        expect:
        succeeds("build")
        ":compileJava" in skippedTasks
        ":build" in executedTasks

        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    static void createJar(File jarFile, String... entries) throws IOException {
        def jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile))
        jarOutputStream.withCloseable {
            entries.each {
                jarOutputStream.putNextEntry(new ZipEntry(it))
            }
        }
    }

}
