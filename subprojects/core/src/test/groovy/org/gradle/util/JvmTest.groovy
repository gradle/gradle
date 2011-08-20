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
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()
    @Rule SetSystemProperties sysProp = new SetSystemProperties()
    OperatingSystem os = Mock()
    Jvm jvm = new Jvm(os)

    def "uses system property to determine if compatible with Java 5"() {
        System.properties['java.version'] = '1.5'

        expect:
        jvm.java5Compatible
        !jvm.java6Compatible
    }

    def "uses system property to determine if compatible with Java 6"() {
        System.properties['java.version'] = '1.6'

        expect:
        jvm.java5Compatible
        jvm.java6Compatible
    }

    def "looks for runtime Jar in Java home directory"() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        TestFile runtimeJar = javaHomeDir.file('lib/rt.jar').createFile()
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        jvm.javaHome == javaHomeDir
        jvm.runtimeJar == runtimeJar
    }

    def "looks for tools Jar in Java home directory"() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        TestFile toolsJar = javaHomeDir.file('lib/tools.jar').createFile()
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def "looks for tools Jar in parent of JRE's Java home directory"() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        TestFile toolsJar = javaHomeDir.file('lib/tools.jar').createFile()
        System.properties['java.home'] = javaHomeDir.file('jre').absolutePath

        expect:
        def jvm = new Jvm(os)
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def "looks for tools Jar in sibling of JRE's Java home directory on Windows"() {
        TestFile javaHomeDir = tmpDir.createDir('jdk1.6.0')
        TestFile toolsJar = javaHomeDir.file('lib/tools.jar').createFile()
        System.properties['java.home'] = tmpDir.createDir('jre6').absolutePath
        System.properties['java.version'] = '1.6.0'
        _ * os.windows >> true

        expect:
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def "uses system property to locate Java home directory when tools Jar not found"() {
        TestFile javaHomeDir = tmpDir.createDir('jdk')
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == null
    }

    def "uses system property to determine if Apple JVM"() {
        when:
        System.properties['java.vm.vendor'] = 'Apple Inc.'
        def jvm = Jvm.current()

        then:
        jvm.getClass() == Jvm.AppleJvm

        when:
        System.properties['java.vm.vendor'] = 'Sun'
        jvm = Jvm.current()

        then:
        jvm.getClass() == Jvm
    }
}
