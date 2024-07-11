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

package org.gradle.internal.jvm

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Matchers
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class JvmTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    def setup() {
        JavaVersion.resetCurrent()
        OperatingSystem.resetCurrent()
    }

    def cleanup() {
        JavaVersion.resetCurrent()
        OperatingSystem.resetCurrent()
    }

    def "uses system property to determine if Java 5/6/7"() {
        System.properties['java.version'] = "1.$version" as String

        expect:
        Jvm.current(OperatingSystem.LINUX).javaVersion."java$version"
        !Jvm.current(OperatingSystem.LINUX).javaVersion."java$other1"
        !Jvm.current(OperatingSystem.LINUX).javaVersion."java$other2"

        where:
        version | other1 | other2
        5       | 6      | 7
        6       | 7      | 5
        7       | 5      | 6
    }

    def "JVM are equal when their Java home dirs are the same"() {
        given:
        TestFile installDir = tmpDir.createDir('software')

        expect:
        def jvm = Jvm.discovered(installDir, "1.8.0", 8)
        def jvm2 = Jvm.discovered(installDir, "1.8.0", 8)
        Matchers.strictlyEquals(jvm, jvm2)
    }

    def "Returns current JVM when located using Java home dir"() {
        when:
        def current = Jvm.current()
        def jvm = Jvm.discovered(current.javaHome, current.javaVersion.majorVersion, Integer.parseInt(current.javaVersion.majorVersion))

        then:
        jvm.is(current)
    }

    def "Returns current JVM when located using java.home property"() {
        when:
        def current = Jvm.current()
        def jvm = Jvm.discovered(new File(System.getProperty("java.home")), current.javaVersion.majorVersion, Integer.parseInt(current.javaVersion.majorVersion))

        then:
        jvm.is(current)
    }

    // TODO: This only works for Jvm.current() but is broken for
    // discovered JVMs.
    def "uses system property to determine if IBM JVM"() {
        when:
        System.properties[vendorProperty] = 'IBM Corporation'
        def jvm = Jvm.current()

        then:
        jvm.isIbmJvm()

        where:
        vendorProperty << ['java.vendor', 'java.vm.vendor']
    }

    // TODO: We probably don't want this behavior.
    def "falls back to PATH if executable cannot be found when using default java"() {
        given:
        def home = tmpDir.createDir("home")
        System.properties['java.home'] = home.absolutePath

        def os = Mock(OperatingSystem) {
            getExecutableName(_ as String) >> { String name -> return "${name}.exe" }
            findInPath("foobar") >> new File('/path/foobar.exe')
        }

        when:
        def exec = Jvm.current(os).getExecutable("foobar")

        then:
        exec == new File('/path/foobar.exe')
    }

    // TODO: We probably don't want this behavior.
    def "falls back to current dir if executable cannot be found anywhere"() {
        given:
        def home = tmpDir.createDir("home")
        System.properties['java.home'] = home.absolutePath

        def os = Mock(OperatingSystem) {
            getExecutableName(_ as String) >> { String name -> return "${name}.exe" }
            findInPath("foobar") >> null
        }

        when:
        def exec = Jvm.current(os).getExecutable("foobar")

        then:
        exec == new File('foobar.exe')
    }

    def "filters environment variables"() {
        def env = [
            'APP_NAME_1234': 'App',
            'JAVA_MAIN_CLASS_1234': 'MainClass',
            'OTHER': 'value',
            'TERM_SESSION_ID': '1234',
            'ITERM_SESSION_ID': '1234'
        ]

        expect:
        Jvm.getInheritableEnvironmentVariables(env) == ['OTHER': 'value']
    }
}
