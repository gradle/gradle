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

import org.gradle.api.Action
import org.gradle.internal.IoActions
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

import java.util.jar.JarEntry
import java.util.jar.JarFile

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
        contents.length == 0
    }

    def "creates fat JAR file for multiple input JAR files"() {
        given:
        def outputJar = new File(tmpDir.testDirectory, 'gradle-api.jar')
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFileWithClassFile(jarFile1)
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFileWithClassFile(jarFile2)

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

    def "merges provider-configuration file with the same name"() {
        given:
        def outputJar = new File(tmpDir.testDirectory, 'gradle-api.jar')
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def serviceType = 'org.gradle.internal.service.scopes.PluginServiceRegistry'
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFileWithProviderConfigurationFile(jarFile1, serviceType, 'org.gradle.api.internal.artifacts.DependencyServices')
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFileWithProviderConfigurationFile(jarFile2, serviceType, """

org.gradle.plugin.use.internal.PluginUsePluginServiceRegistry

""")
        def jarFile3 = inputFilesDir.file('lib3.jar')
        createJarFileWithProviderConfigurationFile(jarFile3, serviceType, """
# This is some same file
# Ignore comment
org.gradle.api.internal.tasks.CompileServices
# Too many comments""")

        when:
        relocatedJarCreator.create(outputJar, [jarFile1, jarFile2, jarFile3])

        then:
        1 * progressLoggerFactory.newOperation(GradleImplDepsRelocatedJarCreator) >> progressLogger
        1 * progressLogger.setDescription('Gradle JARs generation')
        1 * progressLogger.setLoggingHeader("Generating JAR file '$outputJar.name'")
        1 * progressLogger.started()
        3 * progressLogger.progress(_)
        1 * progressLogger.completed()
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        def relocatedJar = contents[0]
        relocatedJar == outputJar
        IoActions.withResource(new JarFile(relocatedJar), new Action<JarFile>() {
            void execute(JarFile relocatedJarFile) {
                JarEntry providerConfigJarEntry = relocatedJarFile.getJarEntry("META-INF/services/$serviceType")
                IoActions.withResource(relocatedJarFile.getInputStream(providerConfigJarEntry), new Action<InputStream>() {
                    void execute(InputStream inputStream) {
                        assert inputStream.text == """org.gradle.api.internal.artifacts.DependencyServices
org.gradle.plugin.use.internal.PluginUsePluginServiceRegistry
org.gradle.api.internal.tasks.CompileServices"""
                    }
                })
            }
        })
    }

    private void createJarFileWithClassFile(TestFile jar) {
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

    private void createJarFileWithProviderConfigurationFile(TestFile jar, String serviceType, String serviceProvider) {
        TestFile contents = tmpDir.createDir("contents/$jar.name")
        contents.createFile("META-INF/services/$serviceType") << serviceProvider
        contents.zipTo(jar)
    }
}
