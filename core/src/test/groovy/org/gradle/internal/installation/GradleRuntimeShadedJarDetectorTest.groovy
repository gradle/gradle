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

package org.gradle.internal.installation

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification

class GradleRuntimeShadedJarDetectorTest extends Specification {

    private static final String CLASS_NAME = 'org/gradle/test/Registry'

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def jarFile = tmpDir.file('lib.jar')

    def "throws exception if provided class is null"() {
        when:
        GradleRuntimeShadedJarDetector.isLoadedFrom(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Need to provide valid class reference'
    }

    def "does not find marker file for class loaded from outside of JAR"() {
        expect:
        !GradleRuntimeShadedJarDetector.isLoadedFrom(String.class)
    }

    def "can find marker file contained in fat JAR"() {
        given:
        createJarWithMarkerFile(jarFile)
        def clazz = loadClassForJar()

        expect:
        GradleRuntimeShadedJarDetector.isLoadedFrom(clazz)
    }

    def "cannot find marker file in standard JAR file"() {
        given:
        createJarWithoutMarkerFile(jarFile)
        def clazz = loadClassForJar()

        expect:
        !GradleRuntimeShadedJarDetector.isLoadedFrom(clazz)
    }

    private Class<?> loadClassForJar() {
        def classLoader

        try {
            classLoader = new URLClassLoader(jarFile.toURI().toURL())
            classLoader.loadClass(CLASS_NAME.replace('/', '.'))
        } finally {
            if (classLoader) {
                // method only exists for JDK 7 or later
                classLoader.close()
            }
        }
    }

    private void createJarWithMarkerFile(TestFile jar) {
        handleAsJarFile(jar) { TestFile contents ->
            writeClass(contents)
            contents.createFile(GradleRuntimeShadedJarDetector.MARKER_FILENAME)
        }
    }

    private void createJarWithoutMarkerFile(TestFile jar) {
        handleAsJarFile(jar) { TestFile contents ->
            writeClass(contents)
            contents.createFile('content.txt')
        }
    }

    private void handleAsJarFile(TestFile jar, Closure c = {}) {
        TestFile contents = tmpDir.createDir('contents')
        c(contents)
        contents.zipTo(jar)
    }

    private void writeClass(TestFile contents) {
        TestFile classFile = contents.createFile("${CLASS_NAME}.class")
        ClassNode classNode = new ClassNode()
        classNode.version = Opcodes.V1_6
        classNode.access = Opcodes.ACC_PUBLIC
        classNode.name = CLASS_NAME
        classNode.superName = 'java/lang/Object'

        ClassWriter cw = new ClassWriter(0)
        classNode.accept(cw)

        classFile.withDataOutputStream {
            it.write(cw.toByteArray())
        }
    }
}
