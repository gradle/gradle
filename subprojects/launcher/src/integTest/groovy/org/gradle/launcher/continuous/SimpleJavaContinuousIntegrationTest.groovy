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

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
// NB: there's nothing specific about Java support and continuous.
//     this spec just lays out some more practical use cases than the other targeted tests.
class SimpleJavaContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'java'
        """
    }

    def "can build when no source dir present"() {
        when:
        assert !file("src/main/java").exists()

        then:
        succeeds("build")
        skipped(":compileJava")
        executed(":build")
    }

    def "can build when source dir is removed"() {
        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"

        when:
        if (TestPrecondition.WINDOWS) {
            //the main src dir might be locked, only delete children
            file("src/main/java").listFiles().each {
                assert !it.deleteDir().exists()
            }
        } else {
            assert !file("src/main/java").deleteDir().exists()
        }


        then:
        succeeds()
        executedAndNotSkipped(":compileJava")
        executed(":build")
    }

    def "build is triggered when a new directory is created in the source inputs"() {
        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds("build")

        when:
        file("src/main/java/foo").createDir()

        then:
        succeeds("build")
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
        def testFile = file("src/test/java/TestThing.java") << "class TestThing extends Thing{}"
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
        testFile.text = "class TestThing extends Thing { static public int FLAG = 1; }"

        then:
        succeeds()
        executedAndNotSkipped(":compileTestJava", ":test")
        skipped(":processResources", ":compileJava")

        when:
        resourceFile << "# another change"

        then:
        succeeds()
        executedAndNotSkipped(":processResources", ":test")
        skipped(":compileJava", ":compileTestJava")
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
    @Requires(TestPrecondition.ONLINE)
    def "can resolve dependencies from remote repository"() {
        when:
        def sourceFile = file("src/main/java/Thing.java") << "class Thing {}"

        buildFile << """
            ${mavenCentralRepository()}
            dependencies {
                implementation "log4j:log4j:1.2.17"
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
        jarWithClasses(somelib, Thing: 'class Thing {}')

        file("src/main/java/Foo.java") << "class Foo extends Thing{}"
        buildFile << """
            dependencies {
                implementation files("lib/somelib.jar")
            }
        """

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"

        when:
        jarWithClasses(somelib, Thing: 'class Thing { public void foo () {} }')

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"

        when:
        jarWithClasses(somelib, Thing: 'class Thing { public void bar() {} }')

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"

        when:
        somelib.delete()

        then:
        fails()
        executedAndNotSkipped ":compileJava"
    }

    def "multiple dependencies as inputs from local filesystem"() {
        when:
        def libDir = file('libs').createDir()
        jarWithClasses(libDir.file("somelib.jar"), Thing: 'interface Thing {}')
        def anotherJar = libDir.file("anotherlib.jar")
        jarWithClasses(anotherJar, Thing2: 'interface Thing2 {}')
        file("src/main/java/Foo.java") << "class Foo implements Thing, Thing2{}"
        buildFile << """
            dependencies {
                implementation fileTree("libs/")
            }
        """

        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"

        when:
        anotherJar.delete()

        then:
        fails()
        executedAndNotSkipped ":compileJava"
    }

    def "creation of initial source file triggers build"() {
        file("src/main/java").createDir()

        expect:
        succeeds("build")
        skipped(":compileJava")
        executed(":build")

        when:
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }
}
