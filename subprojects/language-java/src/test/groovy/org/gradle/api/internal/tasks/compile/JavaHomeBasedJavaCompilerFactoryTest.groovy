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

package org.gradle.api.internal.tasks.compile

import org.gradle.internal.Factory
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.tools.JavaCompiler

class JavaHomeBasedJavaCompilerFactoryTest extends Specification {
    Factory<? extends File> currentJvmJavaHomeFactory = Mock()
    Factory<? extends File> systemPropertiesJavaHomeFactory = Mock()
    Factory<? extends JavaCompiler> systemJavaCompilerFactory = Mock()
    JavaHomeBasedJavaCompilerFactory factory = new JavaHomeBasedJavaCompilerFactory(currentJvmJavaHomeFactory, systemPropertiesJavaHomeFactory, systemJavaCompilerFactory)
    JavaCompiler javaCompiler = Mock()
    @Rule TestNameTestDirectoryProvider temporaryFolder

    def "creates Java compiler for matching Java home directory"() {
        TestFile javaHome = temporaryFolder.file('my/test/java/home')

        when:
        JavaCompiler expectedJavaCompiler = factory.create()

        then:
        1 * currentJvmJavaHomeFactory.create() >> javaHome
        1 * systemPropertiesJavaHomeFactory.create() >> javaHome
        1 * systemJavaCompilerFactory.create() >> javaCompiler
        javaCompiler == expectedJavaCompiler
    }

    def "cannot find Java compiler for matching Java home directory"() {
        TestFile javaHome = temporaryFolder.file('my/test/java/home')

        when:
        factory.create()

        then:
        1 * currentJvmJavaHomeFactory.create() >> javaHome
        1 * systemPropertiesJavaHomeFactory.create() >> javaHome
        1 * systemJavaCompilerFactory.create() >> null
        Throwable t = thrown(RuntimeException)
        t.message == 'Cannot find System Java Compiler. Ensure that you have installed a JDK (not just a JRE) and configured your JAVA_HOME system variable to point to the according directory.'
    }

    def "creates Java compiler for mismatching Java home directory"() {
        File originalJavaHomeDir = SystemProperties.instance.javaHomeDir
        TestFile realJavaHome = temporaryFolder.file('my/test/java/home/real')
        TestFile javaHomeFromToolProvidersPointOfView = temporaryFolder.file('my/test/java/home/toolprovider')

        when:
        JavaCompiler expectedJavaCompiler = factory.create()

        then:
        1 * currentJvmJavaHomeFactory.create() >> realJavaHome
        1 * systemPropertiesJavaHomeFactory.create() >> javaHomeFromToolProvidersPointOfView
        1 * systemJavaCompilerFactory.create() >> {
            assert SystemProperties.instance.javaHomeDir == realJavaHome
            javaCompiler
        }
        javaCompiler == expectedJavaCompiler
        originalJavaHomeDir == SystemProperties.instance.javaHomeDir
    }
}
