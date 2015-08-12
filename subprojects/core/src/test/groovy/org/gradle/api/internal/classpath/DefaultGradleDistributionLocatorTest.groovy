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

package org.gradle.api.internal.classpath

import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification
import spock.lang.Unroll

@Requires(TestPrecondition.JDK7_OR_LATER)
// so we can close the classloaders and allow files to be cleaned up
class DefaultGradleDistributionLocatorTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    List<Closeable> loaders = []

    TestFile distDir

    def setup() {
        distDir = tmpDir.createDir("dist")
        distDir.createDir("lib")
        distDir.createDir("lib/plugins")
    }

    def cleanup() {
        CompositeStoppable.stoppable(loaders).stop()
    }

    @Unroll
    def "determines Gradle home by class bundled in JAR located in valid distribution subdirectory '#jarDirectory'"() {
        given:
        TestFile jar = distDir.file("$jarDirectory/mydep-1.2.jar")
        createJarFile(jar)

        when:
        Class clazz = loadClassFromJar(jar)
        def gradleDistributionLocator = new DefaultGradleDistributionLocator(clazz)

        then:
        gradleDistributionLocator.gradleHome == distDir
        gradleDistributionLocator.libDirs == [new File(distDir, 'lib'), new File(distDir, 'lib/plugins')]

        where:
        jarDirectory << ['lib', 'lib/plugins']
    }

    @Unroll
    def "determines Gradle home by class bundled in JAR located in invalid distribution directory '#jarDirectory'"() {
        given:
        TestFile jar = distDir.file("$jarDirectory/mydep-1.2.jar")
        createJarFile(jar)

        when:
        Class clazz = loadClassFromJar(jar)
        def gradleDistributionLocator = new DefaultGradleDistributionLocator(clazz)

        then:
        !gradleDistributionLocator.gradleHome
        gradleDistributionLocator.libDirs == []

        where:
        jarDirectory << ['other', 'other/plugins']
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

    private Class loadClassFromJar(TestFile jar) {
        // This is to prevent the jar file being held open
        URL url = new URL("jar:file://valid_jar_url_syntax.jar!/")
        URLConnection urlConnection = url.openConnection()
        def original = urlConnection.getDefaultUseCaches()
        urlConnection.setDefaultUseCaches(false)

        try {
            URL[] urls = [new URL("jar:${jar.toURI().toURL()}!/")] as URL[]
            URLClassLoader ucl = new URLClassLoader(urls)
            if (ucl instanceof Closeable) {
                loaders << ucl
            }
            Class.forName('org.gradle.MyClass', true, ucl)
        } finally {
            urlConnection.setDefaultUseCaches(original)
        }
    }
}
