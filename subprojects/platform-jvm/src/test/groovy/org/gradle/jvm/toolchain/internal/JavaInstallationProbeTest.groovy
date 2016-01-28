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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.internal.JavaExecAction
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.jvm.toolchain.internal.JavaInstallationProbe.ProbeResult.*

class JavaInstallationProbeTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder

    @Unroll("Can probe version of #jdk is #expectedResult")
    def "probes java installation"() {
        given:
        def execFactory = Mock(ExecActionFactory)
        def javaExec = Mock(JavaExecAction)
        execFactory.newJavaExecAction() >> javaExec
        PrintStream output
        javaExec.setStandardOutput(_) >> {
            def outputStream = it[0]
            output = new PrintStream(outputStream)
            null
        }
        javaExec.execute() >> {
            systemProperties.each {
                output.println(it.value)
            }
            Mock(ExecResult)
        }
        def install = new JavaInstall()

        when:
        def probe = new JavaInstallationProbe(execFactory)
        File javaHome = new File(jdk)
        if (exists) {
            javaHome = temporaryFolder.newFolder(jdk)
            if (!jre) {
                def binDir = new File(javaHome, "bin")
                if (binDir.mkdir()) {
                    File javac = new File(binDir, OperatingSystem.current().getExecutableName('javac'))
                    javac << 'dummy'
                }
            }
        }
        def probeResult = probe.checkJdk(javaHome)
        if (expectedResult == IS_JDK) {
            probe.configure(javaHome, install)
        }

        then:
        probeResult == expectedResult
        if (expectedResult == IS_JDK) {
            assert install.javaVersion == javaVersion
            assert displayName == null || install.displayName == displayName
        }

        where:
        jdk                                   | systemProperties | javaVersion             | displayName    | exists | jre   | expectedResult
        'localGradle'                         | currentGradle()  | JavaVersion.current()   | null           | true   | false | IS_JDK
        'localGradle'                         | currentGradle()  | JavaVersion.current()   | null           | true   | true  | IS_JRE
        'localGradle'                         | currentGradle()  | JavaVersion.current()   | null           | false  | false | NO_SUCH_DIRECTORY
        'openJdk4'                            | openJdk('4')     | JavaVersion.VERSION_1_4 | 'OpenJDK 4'    | true   | false | IS_JDK
        'openJdk5'                            | openJdk('5')     | JavaVersion.VERSION_1_5 | 'OpenJDK 5'    | true   | false | IS_JDK
        'openJdk6'                            | openJdk('6')     | JavaVersion.VERSION_1_6 | 'OpenJDK 6'    | true   | false | IS_JDK
        'openJdk7'                            | openJdk('7')     | JavaVersion.VERSION_1_7 | 'OpenJDK 7'    | true   | false | IS_JDK
        'openJdk8'                            | openJdk('8')     | JavaVersion.VERSION_1_8 | 'OpenJDK 8'    | true   | false | IS_JDK
        'openJdk9'                            | openJdk('9')     | JavaVersion.VERSION_1_9 | 'OpenJDK 9'    | true   | false | IS_JDK
        'openJdk9'                            | openJdk('9')     | JavaVersion.VERSION_1_9 | 'OpenJDK 9'    | true   | true  | IS_JRE
        'oracleJdk4'                          | oracleJdk('4')   | JavaVersion.VERSION_1_4 | 'Oracle JDK 4' | true   | false | IS_JDK
        'oracleJdk5'                          | oracleJdk('5')   | JavaVersion.VERSION_1_5 | 'Oracle JDK 5' | true   | false | IS_JDK
        'oracleJdk6'                          | oracleJdk('6')   | JavaVersion.VERSION_1_6 | 'Oracle JDK 6' | true   | false | IS_JDK
        'oracleJdk7'                          | oracleJdk('7')   | JavaVersion.VERSION_1_7 | 'Oracle JDK 7' | true   | false | IS_JDK
        'oracleJdk8'                          | oracleJdk('8')   | JavaVersion.VERSION_1_8 | 'Oracle JDK 8' | true   | false | IS_JDK
        'oracleJdk9'                          | oracleJdk('9')   | JavaVersion.VERSION_1_9 | 'Oracle JDK 9' | true   | false | IS_JDK
        'oracleJdk9'                          | oracleJdk('9')   | JavaVersion.VERSION_1_9 | 'Oracle JDK 9' | true   | true  | IS_JRE
        'ibmJdk4'                             | ibmJdk('4')      | JavaVersion.VERSION_1_4 | 'IBM JDK 4'    | true   | false | IS_JDK
        'ibmJdk5'                             | ibmJdk('5')      | JavaVersion.VERSION_1_5 | 'IBM JDK 5'    | true   | false | IS_JDK
        'ibmJdk6'                             | ibmJdk('6')      | JavaVersion.VERSION_1_6 | 'IBM JDK 6'    | true   | false | IS_JDK
        'ibmJdk7'                             | ibmJdk('7')      | JavaVersion.VERSION_1_7 | 'IBM JDK 7'    | true   | false | IS_JDK
        'ibmJdk8'                             | ibmJdk('8')      | JavaVersion.VERSION_1_8 | 'IBM JDK 8'    | true   | false | IS_JDK
        'ibmJdk9'                             | ibmJdk('9')      | JavaVersion.VERSION_1_9 | 'IBM JDK 9'    | true   | false | IS_JDK
        'zulu8'                               | zulu('8')        | JavaVersion.VERSION_1_8 | 'Zulu 8'       | true   | false | IS_JDK
        'binary that has invalid output'      | invalidOutput()  | null                    | null           | true   | false | INVALID_JDK
        'binary that returns unknown version' | invalidVersion() | null                    | null           | true   | false | INVALID_JDK

    }

    private static Map<String, String> invalidOutput() {
        // emulates a binary that would not return the correct number of outputs
        [:]
    }

    private static Map<String, String> invalidVersion() {
        // emulates a valid binary that would not return the correct version number
        ['java.version': "bad luck",
         'java.vendor': "Oracle Corporation",
         'os.arch': "amd64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "25.40-b25",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }


    private static Map<String, String> currentGradle() {
        ['java.version', 'java.vendor', 'os.arch', 'java.vm.name', 'java.vm.version', 'java.runtime.name'].collectEntries { [it, System.getProperty(it)] }
    }

    private static Map<String, String> openJdk(String version) {
        ['java.version': "1.${version}.0",
         'java.vendor': "Oracle Corporation",
         'os.arch': "amd64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "25.40-b25",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

    private static Map<String, String> oracleJdk(String version) {
        ['java.version': "1.${version}.0",
         'java.vendor': "Oracle Corporation",
         'os.arch': "amd64",
         'java.vm.name': "Java HotSpot(TM) 64-Bit Server VM`",
         'java.vm.version': "25.40-b25",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

    private static Map<String, String> ibmJdk(String version) {
        ['java.version': "1.${version}.0",
         'java.vendor': "IBM Corporation",
         'os.arch': "amd64",
         'java.vm.name': "IBM J9 VM",
         'java.vm.version': "2.4",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

   private static Map<String, String> zulu(String version) {
        ['java.version': "1.${version}.0_66",
         'java.vendor': "Azul Systems, Inc.",
         'os.arch': "amd64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "25.66-b17",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    private static class JavaInstall implements InstalledJdk {
        String name
        JavaVersion javaVersion
        String displayName
        File javaHome
    }

}
