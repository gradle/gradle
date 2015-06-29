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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.JavaHomeException
import org.gradle.internal.jvm.JavaInfo
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Unroll

@IgnoreIf({ DefaultModuleRegistryIntegrationTest.availableJdksWithJavac().size() == 0 })
class DefaultModuleRegistryIntegrationTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    TestFile distDir

    def setup() {
        distDir = tmpDir.createDir("dist")
        distDir.createDir("lib")
        distDir.createDir("lib/plugins")
    }

    @Unroll
    def "determines Gradle home by class bundled in JAR located in valid distribution subdirectory '#jarDirectory'"() {
        given:
        TestFile jar = distDir.file("$jarDirectory/mydep-1.2.jar")
        createJarFile(jar)

        when:
        Class clazz = loadClassFromJar(jar)
        def registry = new DefaultModuleRegistry(clazz)

        then:
        registry.gradleHome == distDir

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
        def registry = new DefaultModuleRegistry(clazz)

        then:
        !registry.gradleHome

        where:
        jarDirectory << ['other', 'other/plugins']
    }

    private void createJarFile(TestFile jar) {
        TestFile contents = tmpDir.createDir('contents')
        TestFile javaFile = contents.createFile('org/gradle/MyClass.java')
        javaFile << """package org.gradle;

            public class MyClass {}
        """
        new AntBuilder().javac(destdir: contents, includeantruntime: true) {
            src(path: contents)
        }

        contents.zipTo(jar)
    }

    private Class loadClassFromJar(TestFile jar) {
        URL[] urls = [new URL("jar:${jar.toURI().toURL()}!/")] as URL[]
        URLClassLoader ucl = new URLClassLoader(urls)
        Class.forName('org.gradle.MyClass', true, ucl)
    }

    static List<JavaInfo> availableJdksWithJavac() {
        AvailableJavaHomes.availableJdks.findAll {
            try {
                if (it.javacExecutable) {
                    return true
                }
            }
            catch (JavaHomeException ignore) {
                // ignore
            }
            false
        }
    }
}
