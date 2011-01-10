/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util

import spock.lang.Specification
import org.junit.Rule

class JvmTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    @Rule public final SetSystemProperties sysProp = new SetSystemProperties()
    final OperatingSystem os = Mock()

    def usesSystemPropertyToDetermineIfCompatibleWithJava5() {
        System.properties['java.version'] = '1.5'

        expect:
        def jvm = Jvm.current()
        jvm.java5Compatible
        !jvm.java6Compatible
    }

    def usesSystemPropertyToDetermineIfCompatibleWithJava6() {
        System.properties['java.version'] = '1.6'

        expect:
        def jvm = Jvm.current()
        jvm.java5Compatible
        jvm.java6Compatible
    }

    def looksForToolsJarInJavaHomeDirectory() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        TestFile toolsJar = javaHomeDir.file('lib/tools.jar').createFile()
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        def jvm = Jvm.current()
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def looksForToolsJarInParentOfJavaHomeDirectory() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        TestFile toolsJar = javaHomeDir.file('lib/tools.jar').createFile()
        System.properties['java.home'] = javaHomeDir.file('jre').absolutePath

        expect:
        def jvm = Jvm.current()
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def looksForToolsJarInSiblingOfJavaHomeDirectoryOnWindows() {
        TestFile javaHomeDir = tmpDir.createDir('jdk1.6.0')
        TestFile toolsJar = javaHomeDir.file('lib/tools.jar').createFile()
        System.properties['java.home'] = tmpDir.createDir('jre6').absolutePath
        System.properties['java.version'] = '1.6.0'
        _ * os.windows >> true

        expect:
        def jvm = new Jvm(os)
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def usesSystemPropertyToLocateJavaHomeWhenToolsJarNotFound() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        def jvm = Jvm.current()
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == null
    }

    def usesSystemPropertyToDetermineIfAppleJvm() {

        when:
        System.properties['java.vm.vendor'] = 'Apple Inc.'
        def jvm = Jvm.current()

        then:
        jvm.class == Jvm.AppleJvm

        when:
        System.properties['java.vm.vendor'] = 'Sun'
        jvm = Jvm.current()

        then:
        jvm.class == Jvm
    }

    def appleJvmFiltersEnvironmentVariables() {
        Map<String, String> env = ['APP_NAME_1234': 'App', 'JAVA_MAIN_CLASS_1234': 'MainClass', 'OTHER': 'value']

        expect:
        def jvm = new Jvm.AppleJvm(os)
        jvm.getInheritableEnvironmentVariables(env) == ['OTHER': 'value']
    }
}
