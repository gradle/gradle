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

class CachedPlatformScalaCompileIntegrationTest extends AbstractCachedCompileIntegrationTest {

    String compilationTask = ':compileMainJarMainScala'
    String compiledFile = "build/classes/main/jar/Person.class"

    @Override
    def setupProjectInDirectory(TestFile project) {
        project.with {
            file('settings.gradle') << localCacheConfiguration()
            def scalaFixture = new LanuageScalaCompilationFixture(project)
            scalaFixture.baseline()
            file('build.gradle').text = scalaFixture.buildScript()
        }
    }

    def "joint Java and Scala compilation cannot be cached due to overlapping outputs"() {
        given:
        buildScript """
            plugins {
                id 'jvm-component'
                id 'java-lang'
                id 'scala-lang'
            }
            
            ${mavenCentralRepository()}

            model {
                components {
                    main(JvmLibrarySpec)
                }
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
        def compiledJavaClass = file('/build/classes/main/jar/RequiredByScala.class')
        def compiledScalaClass = file('/build/classes/main/jar/UsesJava.class')

        when:
        withBuildCache().succeeds 'compileMainJarMainJava', compilationTask, '--info'

        then:
        compiledJavaClass.exists()
        compiledScalaClass.exists()
        output.contains "Caching disabled for task ':compileMainJarMainScala': Gradle does not know how file '"
    }

    def "incremental compilation works with caching"() {
        def warmupDir = testDirectory.file('warmupCache')
        setupProjectInDirectory(warmupDir)
        warmupDir.file('settings.gradle') << localCacheConfiguration()

        def classes = new LanuageScalaCompilationFixture(warmupDir)
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
        classes = new LanuageScalaCompilationFixture(testDirectory)
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
