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
    OperatingSystem os = Mock() {
        getExecutableName(_) >> { String name ->
            return "${name}.exe"
        }
    }
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

    def "locates JDK and JRE installs when java.home points to a typical JRE installation embedded in a JDK installation"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jdk {
                lib {
                    file 'tools.jar'
                }
                bin {
                    file 'java.exe'
                    file 'javac.exe'
                    file 'javadoc.exe'
                }
                jre {
                    lib { file 'rt.jar' }
                    bin { file 'java.exe' }
                }
            }
        }

        when:
        System.properties['java.home'] = software.file('jdk/jre').absolutePath

        then:
        jvm.javaHome == software.file('jdk')
        jvm.runtimeJar == software.file('jdk/jre/lib/rt.jar')
        jvm.toolsJar == software.file('jdk/lib/tools.jar')
        jvm.javaExecutable == software.file('jdk/bin/java.exe')
        jvm.javacExecutable == software.file('jdk/bin/javac.exe')
        jvm.javadocExecutable == software.file('jdk/bin/javadoc.exe')
        jvm.jre.homeDir == software.file('jdk/jre')
        jvm.standaloneJre == null
    }

    def "locates JDK and JRE installs when java.home points to a typical JDK installation"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jdk {
                lib {
                    file 'tools.jar'
                }
                bin {
                    file 'java.exe'
                    file 'javac.exe'
                    file 'javadoc.exe'
                }
                jre {
                    lib { file 'rt.jar' }
                    bin { file 'java.exe' }
                }
            }
        }

        when:
        System.properties['java.home'] = software.file('jdk').absolutePath

        then:
        jvm.javaHome == software.file('jdk')
        jvm.runtimeJar == software.file('jdk/jre/lib/rt.jar')
        jvm.toolsJar == software.file('jdk/lib/tools.jar')
        jvm.javaExecutable == software.file('jdk/bin/java.exe')
        jvm.javacExecutable == software.file('jdk/bin/javac.exe')
        jvm.javadocExecutable == software.file('jdk/bin/javadoc.exe')
        jvm.jre.homeDir == software.file('jdk/jre')
        jvm.standaloneJre == null
    }

    def "locates JDK and JRE installs when java.home points to a typical standalone JRE installation"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jre {
                bin { file 'java.exe' }
                lib { file 'rt.jar' }
            }
        }

        when:
        System.properties['java.home'] = software.file('jre').absolutePath

        then:
        jvm.javaHome == software.file('jre')
        jvm.runtimeJar == software.file('jre/lib/rt.jar')
        jvm.toolsJar == null
        jvm.javaExecutable == software.file('jre/bin/java.exe')
        jvm.javacExecutable == new File('javac.exe')
        jvm.javadocExecutable == new File('javadoc.exe')
        jvm.jre.homeDir == software.file('jre')
        jvm.standaloneJre.homeDir == software.file('jre')
    }

    def "locates JDK and JRE installs when java.home points to a typical standalone JRE installation on Windows"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            "${jreDirName}" {
                bin { file 'java.exe' }
                lib { file 'rt.jar' }
            }
            "${jdkDirName}" {
                bin {
                    file 'java.exe'
                    file 'javac.exe'
                    file 'javadoc.exe'
                }
                lib { file 'tools.jar' }
            }
        }
        def jreDir = software.file(jreDirName)
        def jdkDir = software.file(jdkDirName)

        and:
        _ * os.windows >> true

        when:
        System.properties['java.home'] = jreDir.absolutePath
        System.properties['java.version'] = version

        then:
        jvm.javaHome == jdkDir
        jvm.runtimeJar == jreDir.file("lib/rt.jar")
        jvm.toolsJar == jdkDir.file("lib/tools.jar")
        jvm.javaExecutable == jdkDir.file('bin/java.exe')
        jvm.javacExecutable == jdkDir.file('bin/javac.exe')
        jvm.javadocExecutable == jdkDir.file('bin/javadoc.exe')
        jvm.jre.homeDir == jreDir
        jvm.standaloneJre.homeDir == jreDir

        where:
        version    | jreDirName    | jdkDirName
        '1.6.0'    | 'jre6'        | 'jdk1.6.0'
        '1.5.0_22' | 'jre1.5.0_22' | 'jdk1.5.0_22'
    }

    def "locates JDK and JRE installs when java.home points to a typical JDK installation on Windows"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            "${jreDirName}" {
                bin { file 'java.exe' }
                lib {
                    file 'rt.jar'
                }
            }
            "${jdkDirName}" {
                bin {
                    file 'java.exe'
                    file 'javac.exe'
                    file 'javadoc.exe'
                }
                jre {
                    lib {
                        file 'rt.jar'
                    }
                }
                lib {
                    file 'tools.jar'
                }
            }
        }
        def jreDir = software.file(jreDirName)
        def jdkDir = software.file(jdkDirName)

        and:
        _ * os.windows >> true

        when:
        System.properties['java.home'] = jdkDir.absolutePath
        System.properties['java.version'] = version

        then:
        jvm.javaHome == jdkDir
        jvm.runtimeJar == jdkDir.file("jre/lib/rt.jar")
        jvm.toolsJar == jdkDir.file("lib/tools.jar")
        jvm.javaExecutable == jdkDir.file('bin/java.exe')
        jvm.javacExecutable == jdkDir.file('bin/javac.exe')
        jvm.javadocExecutable == jdkDir.file('bin/javadoc.exe')
        jvm.jre.homeDir == jdkDir.file('jre')
        jvm.standaloneJre.homeDir == jreDir

        where:
        version    | jreDirName    | jdkDirName
        '1.6.0'    | 'jre6'        | 'jdk1.6.0'
        '1.5.0_22' | 'jre1.5.0_22' | 'jdk1.5.0_22'
    }

    def "uses system property to determine if Sun/Oracle JVM"() {
        when:
        System.properties['java.vm.vendor'] = 'Sun'
        def jvm = Jvm.create(null)

        then:
        jvm.getClass() == Jvm
    }

    def "uses system property to determine if Apple JVM"() {
        when:
        System.properties['java.vm.vendor'] = 'Apple Inc.'
        def jvm = Jvm.create(null)

        then:
        jvm.getClass() == Jvm.AppleJvm

        when:
        System.properties['java.vm.vendor'] = 'Sun'
        jvm = Jvm.create(null)

        then:
        jvm.getClass() == Jvm
    }

    def "uses system property to determine if IBM JVM"() {
        when:
        System.properties['java.vm.vendor'] = 'IBM Corporation'
        def jvm = Jvm.create(null)

        then:
        jvm.getClass() == Jvm.IbmJvm
    }

    def "finds executable for java home supplied"() {
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
        _ * os.findInPath("foobar") >> new File('/path/foobar.exe')

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
