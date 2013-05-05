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

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class JvmTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule SetSystemProperties sysProp = new SetSystemProperties()
    OperatingSystem os = Mock()
    OperatingSystem theOs = OperatingSystem.current()

    Jvm getJvm() {
        new Jvm(os)
    }

    def "uses system property to determine if Java 5/6/7"() {
        System.properties['java.version'] = "1.$version" as String

        expect:
        jvm.javaVersion."java$version"
        !jvm.javaVersion."java$other1"
        !jvm.javaVersion."java$other2"

        where:
        version | other1 | other2
        5       | 6      | 7
        6       | 7      | 5
        7       | 5      | 6
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

    def "provides information when typical jdk installed"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jdk {
                jre { lib { file 'rt.jar' }}
                lib { file 'tools.jar'}
            }
        }

        when:
        System.properties['java.home'] = software.file('jdk/jre').absolutePath

        then:
        jvm.javaHome.absolutePath == software.file('jdk').absolutePath
        jvm.runtimeJar == software.file('jdk/jre/lib/rt.jar')
        jvm.toolsJar == software.file('jdk/lib/tools.jar')
    }

    def "provides information when typical jre installed"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jre { lib { file 'rt.jar' }}
        }

        when:
        System.properties['java.home'] = software.file('jre').absolutePath

        then:
        jvm.javaHome.absolutePath == software.file('jre').absolutePath
        jvm.runtimeJar == software.file('jre/lib/rt.jar')
        jvm.toolsJar == null
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

    def "uses system property to determine if IBM JVM"() {
        when:
        System.properties['java.vm.vendor'] = 'IBM Corporation'
        def jvm = Jvm.current()

        then:
        jvm.getClass() == Jvm.IbmJvm
    }

    def "finds executable if for java home supplied"() {
        System.properties['java.vm.vendor'] = 'Sun'

        when:
        def home = tmpDir.createDir("home")
        home.create {
            jre {
                bin {
                    file theOs.getExecutableName('java')
                    file theOs.getExecutableName('javadoc')
                }
            }
        }

        then:
        home.file(theOs.getExecutableName("jre/bin/javadoc")).absolutePath ==
            Jvm.forHome(home.file("jre")).getExecutable("javadoc").absolutePath
    }

    def "finds tools.jar if java home supplied"() {
        System.properties['java.vm.vendor'] = 'Sun'

        when:
        def home = tmpDir.createDir("home")
        home.create {
            jdk {
                bin { file theOs.getExecutableName('java') }
                lib { file 'tools.jar' }
            }
        }

        then:
        home.file("jdk/lib/tools.jar").absolutePath ==
            Jvm.forHome(home.file("jdk")).toolsJar.absolutePath
    }

    def "provides decent feedback if executable not found"() {
        given:
        def home = tmpDir.createDir("home")
        home.create {
            bin { file theOs.getExecutableName('java') }
        }

        when:
        Jvm.forHome(home).getExecutable("foobar")

        then:
        def ex = thrown(JavaHomeException)
        ex.message.contains('foobar')
    }

    def "falls back to PATH if executable cannot be found when using default java"() {
        given:
        def home = tmpDir.createDir("home")
        System.properties['java.home'] = home.absolutePath
        1 * os.getExecutableName(_ as String) >> "foobar.exe"
        1 * os.findInPath("foobar") >> new File('/path/foobar.exe')

        when:
        def exec = jvm.getExecutable("foobar")

        then:
        exec == new File('/path/foobar.exe')
    }

    def "falls back to current dir if executable cannot be found anywhere"() {
        given:
        def home = tmpDir.createDir("home")
        System.properties['java.home'] = home.absolutePath

        os.getExecutableName(_ as String) >> "foobar.exe"
        1 * os.findInPath("foobar") >> null

        when:
        def exec = jvm.getExecutable("foobar")

        then:
        exec == new File('foobar.exe')
    }

    def "provides decent feedback for invalid java home"() {
        given:
        def someHome = tmpDir.createDir("someHome")

        when:
        Jvm.forHome(someHome)

        then:
        def ex = thrown(JavaHomeException)
        ex.message.contains('someHome')
    }

    def "provides basic validation for java home"() {
        when:
        Jvm.forHome(new File('i dont exist'))

        then:
        thrown(IllegalArgumentException)
    }
    
    def "describes accurately when created for supplied java home"() {
        when:
        def jvm = new Jvm(theOs, new File('dummyFolder'))

        then:
        jvm.toString().contains('dummyFolder')
    }
}
