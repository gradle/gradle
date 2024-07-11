/*
 * Copyright 2024 the original author or authors.
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
import org.junit.Rule
import spock.lang.Specification

class DefaultJvmInfoTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "finds javadoc executable for embedded jre when parent dir does not have java"() {
        given:
        def home = tmpDir.createDir("home")
        def jre = home.file("jre")
        jre.file("bin/java").touch()

        def javadoc = jre.file("bin/" + OperatingSystem.current().getExecutableName("javadoc"))
        javadoc.touch()

        when:
        def jvm = DefaultJavaInfo.forHome(home.file("jre"), OperatingSystem.LINUX)

        then:
        jvm.getJavadocExecutable().absolutePath == javadoc.absolutePath
    }

    def "finds javadoc executable for parent jdk of jre when parent dir does has java"() {
        given:
        def jdk = tmpDir.createDir("home")
        def jre = jdk.file("jre")
        jre.file("bin/java").touch()
        jdk.file("bin/java").touch()

        def jreJavadoc = jre.file("bin/" + OperatingSystem.current().getExecutableName("javadoc"))
        jreJavadoc.touch()

        def jdkJavadoc = jdk.file("bin/" + OperatingSystem.current().getExecutableName("javadoc"))
        jdkJavadoc.touch()

        when:
        def jvm = DefaultJavaInfo.forHome(jdk.file("jre"), OperatingSystem.LINUX)

        then:
        jvm.getJavadocExecutable().absolutePath == jdkJavadoc.absolutePath
    }

    def "finds tools.jar if java home supplied"() {
        when:
        def home = tmpDir.createDir("home")
        home.file("bin/java").touch()
        def tools = home.file("lib/tools.jar")
        tools.touch()


        then:
        def jvm = DefaultJavaInfo.forHome(home, OperatingSystem.LINUX)
        jvm.toolsJar.absolutePath == tools.absolutePath
    }

    def "provides decent feedback if executable not found"() {
        given:
        def home = tmpDir.createDir("home")
        home.file("bin/java").touch()

        when:
        def jvm = DefaultJavaInfo.forHome(home, OperatingSystem.LINUX)
        jvm.getExecutable("foobar")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('foobar')
    }

    def "returns null for non-existent java homes"() {
        expect:
        DefaultJavaInfo.forHome(new File("i don't exist")) == null
    }

    def "locates JDK and JRE installs for a typical JRE installation embedded in a Java 8 JDK installation"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jdk {
                lib {
                    file 'tools.jar'
                }
                bin {
                    file 'java'
                    file 'javac'
                    file 'javadoc'
                }
                jre {
                    lib { file 'rt.jar' }
                    bin { file 'java' }
                }
            }
        }

        when:
        JavaInfo jvm = DefaultJavaInfo.forHome(software.file('jdk/jre'), OperatingSystem.LINUX)

        then:
        jvm.javaHome == software.file('jdk')
        jvm.jdk
        jvm.toolsJar == software.file('jdk/lib/tools.jar')
        jvm.javaExecutable == software.file('jdk/bin/java')
        jvm.javacExecutable == software.file('jdk/bin/javac')
        jvm.javadocExecutable == software.file('jdk/bin/javadoc')
        jvm.embeddedJre == software.file('jdk/jre')
        jvm.standaloneJre == null
        jvm.jre == jvm.embeddedJre
    }

    def "locates JDK and JRE installs for a typical Java 8 JDK installation"() {
        given:
        def software = tmpDir.createDir('software')
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
        def jvm = DefaultJavaInfo.forHome(software.file('jdk'), OperatingSystem.WINDOWS)

        then:
        jvm.javaHome == software.file('jdk')
        jvm.jdk
        jvm.toolsJar == software.file('jdk/lib/tools.jar')
        jvm.javaExecutable == software.file('jdk/bin/java.exe')
        jvm.javacExecutable == software.file('jdk/bin/javac.exe')
        jvm.javadocExecutable == software.file('jdk/bin/javadoc.exe')
        jvm.jre == software.file('jdk/jre')
        jvm.embeddedJre == software.file("jdk/jre")
        jvm.standaloneJre == null
        jvm.jre == jvm.embeddedJre
    }

    def "locates JDK install for a typical Java 9 JDK installation"() {
        given:
        def software = tmpDir.createDir('software')
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
            }
        }

        when:
        def jvm = DefaultJavaInfo.forHome(software.file('jdk'), OperatingSystem.WINDOWS)

        then:
        jvm.javaHome == software.file('jdk')
        jvm.jdk
        jvm.toolsJar == software.file('jdk/lib/tools.jar')
        jvm.javaExecutable == software.file('jdk/bin/java.exe')
        jvm.javacExecutable == software.file('jdk/bin/javac.exe')
        jvm.javadocExecutable == software.file('jdk/bin/javadoc.exe')
        jvm.embeddedJre == null
        jvm.standaloneJre == null
        jvm.jre == null
    }

    def "locates JRE install for a typical standalone Java 8 JRE installation"() {
        given:
        TestFile software = tmpDir.createDir('software')
        software.create {
            jre {
                bin { file 'java.exe' }
                lib { file 'rt.jar' }
            }
        }

        when:
        def jvm = DefaultJavaInfo.forHome(software.file('jre'), OperatingSystem.WINDOWS)

        then:
        jvm.javaHome == software.file('jre')
        !jvm.jdk
        jvm.toolsJar == null
        jvm.javaExecutable == software.file('jre/bin/java.exe')
        jvm.embeddedJre == null
        jvm.standaloneJre == null
        jvm.jre == null
    }

    def "locates JDK and JRE installs for a typical JRE installation alongside Java 8 JDK installation on Windows"() {
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

        when:
        def jvm = DefaultJavaInfo.forHome(jdkDir, OperatingSystem.WINDOWS)

        then:
        jvm.javaHome == jdkDir
        jvm.jdk
        jvm.toolsJar == jdkDir.file("lib/tools.jar")
        jvm.javaExecutable == jdkDir.file('bin/java.exe')
        jvm.javacExecutable == jdkDir.file('bin/javac.exe')
        jvm.javadocExecutable == jdkDir.file('bin/javadoc.exe')
        jvm.embeddedJre == null
        jvm.standaloneJre == jreDir
        jvm.jre == jvm.standaloneJre

        where:
        version    | jreDirName    | jdkDirName
        '1.6.0'    | 'jre6'        | 'jdk1.6.0'
        '1.5.0_22' | 'jre1.5.0_22' | 'jdk1.5.0_22'
    }

    def "locates JDK and JRE installs for a typical Java 8 JDK installation on Windows"() {
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

        when:
        def jvm = DefaultJavaInfo.forHome(jdkDir, OperatingSystem.WINDOWS)

        then:
        jvm.javaHome == jdkDir
        jvm.jdk
        jvm.toolsJar == jdkDir.file("lib/tools.jar")
        jvm.javaExecutable == jdkDir.file('bin/java.exe')
        jvm.javacExecutable == jdkDir.file('bin/javac.exe')
        jvm.javadocExecutable == jdkDir.file('bin/javadoc.exe')
        jvm.embeddedJre == jdkDir.file('jre')
        jvm.standaloneJre == jreDir
        jvm.jre == jvm.embeddedJre

        where:
        version    | jreDirName    | jdkDirName
        '1.6.0'    | 'jre6'        | 'jdk1.6.0'
        '1.5.0_22' | 'jre1.5.0_22' | 'jdk1.5.0_22'
    }

    def "describes accurately when created for supplied java home"() {
        def jdkDir = tmpDir.createDir('dummyFolder')
        jdkDir.file("bin/java").touch()

        when:
        def jvm = DefaultJavaInfo.forHome(jdkDir, OperatingSystem.LINUX)

        then:
        jvm.toString().contains('dummyFolder')
    }

    def "locates MAC OS JDK9 install when java.home points to an EAP JDK 1.9 installation"() {
        given:

        TestFile software = tmpDir.createDir('software')
        //http://openjdk.java.net/jeps/220
        software.create {
            Contents {
                Home {
                    bin {
                        file 'java'
                        file 'javac'
                        file 'javadoc'
                    }
                    conf {
                        'logging.properties'
                    }
                    lib {

                    }
                }
            }
        }

        when:
        JavaInfo java9Vm = DefaultJavaInfo.forHome(software.file('Contents/Home'), OperatingSystem.MAC_OS)

        then:
        java9Vm.javaHome == software.file('Contents/Home')
        java9Vm.javaExecutable == software.file('Contents/Home/bin/java')
        java9Vm.javacExecutable == software.file('Contents/Home/bin/javac')
        java9Vm.javadocExecutable == software.file('Contents/Home/bin/javadoc')
        java9Vm.jre == null
        java9Vm.toolsJar == null
        java9Vm.standaloneJre == null
    }
}
