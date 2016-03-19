/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.impldeps

import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

class GradleImplDepsRelocatedJarCreatorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def progressLogger = Mock(ProgressLogger)
    GradleImplDepsRelocatedJarCreator relocatedJarCreator = new GradleImplDepsRelocatedJarCreator(progressLoggerFactory)

    def "throws exception if non-JAR files are provided"() {
        given:
        def outputJar = new File(tmpDir.testDirectory, 'gradle-api.jar')
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def textFile = inputFilesDir.file('text.txt')

        when:
        relocatedJarCreator.create(outputJar, [textFile])

        then:
        RuntimeException e = thrown(RuntimeException)
        e.message == "non JAR on classpath: $textFile.absolutePath"
        1 * progressLoggerFactory.newOperation(GradleImplDepsRelocatedJarCreator) >> progressLogger
        1 * progressLogger.setDescription('Gradle JARs generation')
        1 * progressLogger.setLoggingHeader("Generating JAR file '$outputJar.name'")
        1 * progressLogger.started()
        1 * progressLogger.progress(_)
        1 * progressLogger.completed()
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == new File(outputJar.parentFile, outputJar.name + ".tmp")
    }

    def "creates fat JAR file for multiple input JAR files"() {
        given:
        def outputJar = new File(tmpDir.testDirectory, 'gradle-api.jar')
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFile(jarFile1)
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFile(jarFile2)

        when:
        relocatedJarCreator.create(outputJar, [jarFile1, jarFile2])

        then:
        1 * progressLoggerFactory.newOperation(GradleImplDepsRelocatedJarCreator) >> progressLogger
        1 * progressLogger.setDescription('Gradle JARs generation')
        1 * progressLogger.setLoggingHeader("Generating JAR file '$outputJar.name'")
        1 * progressLogger.started()
        2 * progressLogger.progress(_)
        1 * progressLogger.completed()
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == outputJar
    }

    private void createJarFile(TestFile jar) {
        TestFile contents = tmpDir.createDir('contents')
        TestFile classFile = contents.createFile('org/gradle/MyClass.class')

        ClassNode classNode = new ClassNode()
        classNode.version = Opcodes.V1_6
        classNode.access = Opcodes.ACC_PUBLIC
        classNode.name = 'org/gradle/MyClass'
        classNode.superName = 'java/lang/Object'

        ClassWriter cw = new ClassWriter(0)
        classNode.accept(cw)

        classFile.withDataOutputStream {
            it.write(cw.toByteArray())
        }

        contents.zipTo(jar)
    }
}
