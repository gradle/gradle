/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.tasks.compile.AbstractCachedCompileIntegrationTest
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.language.scala.PlayCompilationFixture.PLAY_REPOSITORIES

class CachedPlatformScalaCompileIntegrationTest extends AbstractCachedCompileIntegrationTest {

    String compilationTask = ':compilePlayBinaryScala'
    String compiledFile = "build/playBinary/classes/Person.class"

    @Override
    def setupProjectInDirectory(TestFile project = temporaryFolder.testDirectory) {
        project.with {
            file('settings.gradle') << localCacheConfiguration()
            def playFixture = new PlayCompilationFixture(project)
            playFixture.baseline()
            file('build.gradle').text = playFixture.buildScript()
        }
    }

    def "joint Java and Scala compilation can be cached"() {
        given:
        buildScript """
            plugins {
                id 'play'
                id 'java'
            }
          
            ${PLAY_REPOSITORIES}
        """
        file('app/controller/RequiredByScala.java') << """
            public class RequiredByScala {
                public static void printSomething() {
                    java.lang.System.out.println("Hello from Java");
                }
            }
        """
        file('app/controller/RequiredByScala.java').makeOlder()

        file('app/controller/UsesJava.scala') << """
            class UsesJava {
                def printSomething(): Unit = {
                    RequiredByScala.printSomething()
                }
            }
        """
        file('app/controller/UsesJava.scala').makeOlder()
        def compiledJavaClass = file('/build/playBinary/classes/RequiredByScala.class')
        def compiledScalaClass = file('/build/playBinary/classes/UsesJava.class')

        when:
        withBuildCache().succeeds ':compileJava', compilationTask

        then:
        compiledJavaClass.exists()
        compiledScalaClass.exists()

        when:
        withBuildCache().succeeds ':clean', ':compileJava'

        then:
        skipped ':compileJava'

        when:
        // This line is crucial to expose the bug
        // When doing this and then loading the classes for
        // compileScala from the cache the compiled java
        // classes are replaced and recorded as changed
        def javaClassRequiredByScala = file('/build/playBinary/classes/RequiredByScala.class')
        if(javaClassRequiredByScala.lastModified() == 0){
            javaClassRequiredByScala.setLastModified(Calendar.getInstance().getTime().minus(1).seconds)
        } else {
            javaClassRequiredByScala.setLastModified(javaClassRequiredByScala.lastModified() - 2000L)
        }
        withBuildCache().succeeds compilationTask

        then:
        skipped compilationTask

        when:
        file('app/controller/RequiredByScala.java').text = """
            public class RequiredByScala {
                public static void printSomethingNew() {
                    java.lang.System.out.println("Hello from Java");
                    // Different
                }
            }
        """
        file('app/controller/UsesJava.scala').text = """
            class UsesJava {
                def printSomething(): Unit = {
                    RequiredByScala.printSomethingNew()
                    // Some comment
                }
            }
        """

        withBuildCache().succeeds compilationTask

        then:
        compiledJavaClass.exists()
        compiledScalaClass.exists()
    }

    def "incremental compilation works with caching"() {
        def warmupDir = testDirectory.file('warmupCache')
        setupProjectInDirectory(warmupDir)
        warmupDir.file('settings.gradle') << localCacheConfiguration()

        def classes = new PlayCompilationFixture(warmupDir)
        classes.baseline()
        classes.classDependingOnBasicClassSource.change()

        when:
        executer.inDirectory(warmupDir)
        withBuildCache().succeeds compilationTask

        then:
        classes.all*.compiledClass*.exists().every()
        classes.analysisFile.assertIsFile()

        when:
        executer.inDirectory(warmupDir)
        withBuildCache().succeeds compilationTask

        then:
        skipped compilationTask

        when:
        warmupDir.deleteDir()
        setupProjectInDirectory(testDirectory)
        executer.inDirectory(testDirectory)
        classes = new PlayCompilationFixture(testDirectory)
        classes.baseline()
        withBuildCache().succeeds compilationTask

        then:
        executedAndNotSkipped compilationTask
        classes.analysisFile.assertIsFile()

        when:
        classes.classDependingOnBasicClassSource.change()
        withBuildCache().succeeds compilationTask

        then:
        skipped compilationTask
        // Local state is removed when loaded from cache
        classes.analysisFile.assertDoesNotExist()

        when:
        cleanBuildDir()
        withBuildCache().succeeds compilationTask

        then:
        skipped compilationTask
        // Local state is removed when loaded from cache
        classes.analysisFile.assertDoesNotExist()

        when:
        // Make sure we notice when classes are recompiled
        classes.all*.compiledClass*.makeOlder()
        classes.independentClassSource.change()
        withBuildCache().succeeds compilationTask

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
