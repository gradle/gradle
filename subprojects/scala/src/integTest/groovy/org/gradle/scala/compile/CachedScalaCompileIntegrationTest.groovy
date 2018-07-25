/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.api.tasks.compile.AbstractCachedCompileIntegrationTest
import org.gradle.scala.ScalaCompilationFixture
import org.gradle.test.fixtures.file.TestFile

class CachedScalaCompileIntegrationTest extends AbstractCachedCompileIntegrationTest {
    String compilationTask = ':compileScala'
    String compiledFile = "build/classes/scala/main/Hello.class"

    @Override
    def setupProjectInDirectory(TestFile project) {
        project.with {
            file('settings.gradle') << localCacheConfiguration()
            file('build.gradle').text = """
            plugins {
                id 'scala'
                id 'application'
            }

            mainClassName = "Hello"

            ${mavenCentralRepository()}

            dependencies {
                compile group: 'org.scala-lang', name: 'scala-library', version: '2.11.12'
            }
        """.stripIndent()

        file('src/main/scala/Hello.scala').text = """
            object Hello {
                def main(args: Array[String]): Unit = {
                    print("Hello!")
                }
            }
        """.stripIndent()
        }
    }

    def "joint Java and Scala compilation can be cached"() {
        given:
        buildScript """
            plugins {
                id 'scala'
            }

            ${mavenCentralRepository()}
            
            dependencies {
                compile group: 'org.scala-lang', name: 'scala-library', version: '2.11.12'
            }
        """
        file('src/main/java/RequiredByScala.java') << """
            public class RequiredByScala {
                public static void printSomething() {
                    java.lang.System.out.println("Hello from Java");
                }
            }
        """
        file('src/main/java/RequiredByScala.java').makeOlder()

        file('src/main/scala/UsesJava.scala') << """
            class UsesJava {
                def printSomething(): Unit = {
                    RequiredByScala.printSomething()
                }
            }
        """
        file('src/main/scala/UsesJava.scala').makeOlder()
        def compiledJavaClass = javaClassFile('RequiredByScala.class')
        def compiledScalaClass = scalaClassFile('UsesJava.class')

        when:
        withBuildCache().run ':compileJava', compilationTask

        then:
        compiledJavaClass.exists()
        compiledScalaClass.exists()

        when:
        withBuildCache().run ':clean', ':compileJava'

        then:
        skipped ':compileJava'

        when:
        // This line is crucial to expose the bug
        // When doing this and then loading the classes for
        // compileScala from the cache the compiled java
        // classes are replaced and recorded as changed
        compiledJavaClass.makeOlder()
        withBuildCache().run compilationTask

        then:
        skipped compilationTask

        when:
        file('src/main/java/RequiredByScala.java').text = """
            public class RequiredByScala {
                public static void printSomethingNew() {
                    java.lang.System.out.println("Hello from Java");
                    // Different
                }
            }
        """
        file('src/main/scala/UsesJava.scala').text = """
            class UsesJava {
                def printSomething(): Unit = {
                    RequiredByScala.printSomethingNew()
                    // Some comment
                }
            }
        """

        withBuildCache().run compilationTask

        then:
        compiledJavaClass.exists()
        compiledScalaClass.exists()
    }

    def "incremental compilation works with caching"() {
        def warmupDir = testDirectory.file('warmupCache')
        setupProjectInDirectory(warmupDir)
        warmupDir.file('settings.gradle') << localCacheConfiguration()

        def classes = new ScalaCompilationFixture(warmupDir)
        classes.baseline()
        classes.classDependingOnBasicClassSource.change()

        when:
        executer.inDirectory(warmupDir)
        withBuildCache().run compilationTask

        then:
        classes.all*.compiledClass*.exists().every()
        classes.analysisFile.assertIsFile()

        when:
        warmupDir.deleteDir()
        setupProjectInDirectory(testDirectory)
        classes = new ScalaCompilationFixture(testDirectory)
        classes.baseline()
        withBuildCache().run compilationTask

        then:
        executedAndNotSkipped compilationTask
        classes.analysisFile.assertIsFile()

        when:
        classes.classDependingOnBasicClassSource.change()
        withBuildCache().run compilationTask

        then:
        skipped compilationTask
        // Local state is removed when loaded from cache
        classes.analysisFile.assertDoesNotExist()

        when:
        cleanBuildDir()
        withBuildCache().run compilationTask

        then:
        skipped compilationTask
        // Local state is removed when loaded from cache
        classes.analysisFile.assertDoesNotExist()

        when:
        // Make sure we notice when classes are recompiled
        classes.all*.compiledClass*.makeOlder()
        classes.independentClassSource.change()
        withBuildCache().run compilationTask

        then:
        executedAndNotSkipped compilationTask
        assertAllRecompiled(classes.allClassesLastModified, old(classes.allClassesLastModified))
        classes.analysisFile.assertIsFile()
    }

    private void cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }

    private static void assertAllRecompiled(List<Long> lastModified, List<Long> oldLastModified) {
        [lastModified, oldLastModified].transpose().each { after, before ->
            assert after != before
        }
    }

}
