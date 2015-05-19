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

import org.gradle.integtests.fixtures.jvm.JvmSourceFile
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class SimpleJavaContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    TestJvmComponent app = new TestJavaComponent()

    // TODO: Fold test sources into TestJvmComponent?
    List<JvmSourceFile> testSources = [
        new JvmSourceFile("compile/test", "PersonTest.java", '''
package compile.test;

public class PersonTest {
    void testIt() {
        assert true;
    }
}''')
    ]

    TestFile sourceDir = file("src/main")
    TestFile testSourceDir = file("src/test/java")
    TestFile resourceDir = file("src/main/resources")

    def setup() {
        buildFile << """
    apply plugin: 'java'
"""
    }

    def "can build in continuous mode when no source dir present"() {
        when:
        assert !sourceDir.exists()
        then:
        succeeds("build")
        ":compileJava" in skippedTasks
        ":build" in executedTasks
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "can build in continuous mode when source dir is removed"() {
        when:
        app.writeSources(sourceDir)
        then:
        sourceDir.exists()
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        when:
        sourceDir.deleteDir()
        assert !sourceDir.exists()
        then:
        succeeds()
        ":compileJava" in skippedTasks
        ":build" in executedTasks
    }

    def "build is not triggered when a new directory is created in the source inputs"() {
        when:
        app.writeSources(sourceDir)
        then:
        sourceDir.exists()
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        when:
        sourceDir.file("newdirectory").createDir()
        then:
        noBuildTriggered()
    }

    def "after compilation failure, fixing file retriggers build"() {
        given:
        def sourceFiles = app.writeSources(sourceDir)
        def sourceFile = sourceFiles.get(0)
        when:
        sourceFile << "/* Broken compile"
        then:
        fails("build")
        when:
        sourceFile << "*/"
        then:
        succeeds()
        executedAndNotSkipped(":compileJava", ":build")
    }

    def "running test in continuous mode"() {
        when:
        def resourceFiles = app.writeResources(resourceDir)
        def testSourceFiles = testSources*.writeToDir(testSourceDir)
        def sourceFiles = app.writeSources(sourceDir)
        def sourceFile = sourceFiles.get(0)
        def testSourceFile = testSourceFiles.get(0)
        def resourceFile = resourceFiles.get(0)
        then:
        succeeds("test")
        executedAndNotSkipped(":compileJava", ":processResources", ":compileTestJava", ":test")

        when: "Change to source file causes execution of tests"
        sourceFile << "class Change {}"
        then:
        succeeds()
        executedAndNotSkipped(":compileJava", ":compileTestJava", ":test")
        skipped(":processResources")

        when: "Change to test file causes execution of tests"
        testSourceFile << "class Change2 {}"
        then:
        succeeds()
        executedAndNotSkipped(":compileTestJava", ":test")
        skipped(":processResources", ":compileJava")

        when: "Change to resource file (src/main/resources)"
        resourceFile << "# another change"
        then:
        succeeds()
        executedAndNotSkipped(":processResources", ":compileTestJava", ":test")
        skipped(":compileJava")
    }

    def "failing main source build ignores changes to test source"() {
        when:
        def testSourceFiles = testSources*.writeToDir(testSourceDir)
        def sourceFiles = app.writeSources(sourceDir)
        def sourceFile = sourceFiles.get(0)
        def testSourceFile = testSourceFiles.get(0)
        sourceFile << "/* Broken build"
        then:
        fails("test")

        when: "Change to test source file does not cause rebuild"
        testSourceFile << "// some change"
        then:
        noBuildTriggered()
    }

    def "can resolve dependencies from remote repository in continuous mode"() {
        when:
        app.writeSources(sourceDir)
        def sourceFile = sourceDir.file("java/Another.java")
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
        executedAndNotSkipped ":compileJava", ":build"

        when:
        sourceFile << """
    // just adding a real dependency on log4j to the compilation
    import org.apache.log4j.LogManager;
    public class Another {}
"""
        then:
        succeeds()
        executedAndNotSkipped ":compileJava", ":build"
    }

    def "dependencies as inputs from local filesystem"() {
        when:
        def somelib = file("lib/somelib.jar")
        somelib.parentFile.mkdir()
        app.writeSources(sourceDir)
        buildFile << """
    dependencies {
        compile files("lib/somelib.jar")
    }
"""
        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        when: "dependency is created"
        createJar(somelib, "META-INF/")
        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
        when: "dependency is changed"
        createJar(somelib, "META-INF/", "another-dir/")
        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
        when: "dependency is removed"
        somelib.delete()
        then:
        succeeds()
        executedAndNotSkipped ":compileJava"
    }

    def "multiple dependencies as inputs from local filesystem"() {
        when:
        def libDir = file('libs').createDir()
        createJar(libDir.file("somelib.jar"), "META-INF/")
        app.writeSources(sourceDir)
        buildFile << """
    dependencies {
        compile fileTree("libs/")
    }
"""
        then:
        succeeds("build")
        executedAndNotSkipped ":compileJava", ":build"
        when: "another dependency is created"
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
        app.writeSources(sourceDir)

        then:
        succeeds()
        executedAndNotSkipped ":compileJava", ":jar", ":build"
    }

    private def createJar(jarFile, String... entries) throws IOException {
        def jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile))
        entries.each {
            // put some empty directories in the jar
            jarOutputStream.putNextEntry(new ZipEntry(it))
        }
        jarOutputStream.close()
    }

    def "Task can specify project directory as a task input; changes are respected"() {
        given:
        buildFile << """
task a {
    inputs.dir projectDir
    doLast {}
}
"""
        expect:
        succeeds("a")
        executedAndNotSkipped(":a")
        when: "new file created"
        file("A").text = "A"
        then:
        succeeds()
        executedAndNotSkipped(":a")
        when: "file is changed"
        file("A").text = "B"
        then:
        succeeds()
        executedAndNotSkipped(":a")
        when: "file is deleted"
        file("A").delete()
        then:
        succeeds()
        executedAndNotSkipped(":a")
    }
}
