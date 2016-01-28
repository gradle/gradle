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
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.internal.JavaExecAction
import spock.lang.Specification
import spock.lang.Unroll

class JavaInstallationProbeTest extends Specification {
    @Unroll("Can probe version of #jdk")
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
        def isValid = probe.isValidInstallation(new File(jdk))
        if (valid) {
            probe.configure(new File(jdk), install)
        }

        then:
        isValid == valid
        if (valid) {
            assert install.javaVersion == javaVersion
            assert displayName == null || install.displayName == displayName
        }

        where:
        jdk                                   | systemProperties | javaVersion             | displayName    | valid
        'localGradle'                         | currentGradle()  | JavaVersion.current()   | null           | true
        'openJdk4'                            | openJdk('4')     | JavaVersion.VERSION_1_4 | 'OpenJDK 4'    | true
        'openJdk5'                            | openJdk('5')     | JavaVersion.VERSION_1_5 | 'OpenJDK 5'    | true
        'openJdk6'                            | openJdk('6')     | JavaVersion.VERSION_1_6 | 'OpenJDK 6'    | true
        'openJdk7'                            | openJdk('7')     | JavaVersion.VERSION_1_7 | 'OpenJDK 7'    | true
        'openJdk8'                            | openJdk('8')     | JavaVersion.VERSION_1_8 | 'OpenJDK 8'    | true
        'openJdk9'                            | openJdk('9')     | JavaVersion.VERSION_1_9 | 'OpenJDK 9'    | true
        'oracleJdk4'                          | oracleJdk('4')   | JavaVersion.VERSION_1_4 | 'Oracle JDK 4' | true
        'oracleJdk5'                          | oracleJdk('5')   | JavaVersion.VERSION_1_5 | 'Oracle JDK 5' | true
        'oracleJdk6'                          | oracleJdk('6')   | JavaVersion.VERSION_1_6 | 'Oracle JDK 6' | true
        'oracleJdk7'                          | oracleJdk('7')   | JavaVersion.VERSION_1_7 | 'Oracle JDK 7' | true
        'oracleJdk8'                          | oracleJdk('8')   | JavaVersion.VERSION_1_8 | 'Oracle JDK 8' | true
        'oracleJdk9'                          | oracleJdk('9')   | JavaVersion.VERSION_1_9 | 'Oracle JDK 9' | true
        'ibmJdk4'                             | ibmJdk('4')      | JavaVersion.VERSION_1_4 | 'IBM JDK 4'    | true
        'ibmJdk5'                             | ibmJdk('5')      | JavaVersion.VERSION_1_5 | 'IBM JDK 5'    | true
        'ibmJdk6'                             | ibmJdk('6')      | JavaVersion.VERSION_1_6 | 'IBM JDK 6'    | true
        'ibmJdk7'                             | ibmJdk('7')      | JavaVersion.VERSION_1_7 | 'IBM JDK 7'    | true
        'ibmJdk8'                             | ibmJdk('8')      | JavaVersion.VERSION_1_8 | 'IBM JDK 8'    | true
        'ibmJdk9'                             | ibmJdk('9')      | JavaVersion.VERSION_1_9 | 'IBM JDK 9'    | true
        'binary that has invalid output'      | invalidOutput()  | null                    | null           | false
        'binary that returns unknown version' | invalidVersion() | null                    | null           | false

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

    private static class JavaInstall implements InstalledJdkInternal {
        String name
        JavaVersion javaVersion
        String displayName
        File javaHome
    }
}
