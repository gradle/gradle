/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.jvm.inspection

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.process.internal.ExecHandleFactory
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.spockframework.runtime.SpockAssertionError
import spock.lang.Specification
import spock.lang.Unroll

class DefaultJvmMetadataDetectorTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder

    private DefaultJvmMetadataDetector createDefaultJvmMetadataDetector(ExecHandleFactory execHandleFactory) {
        return new DefaultJvmMetadataDetector(
            execHandleFactory,
            TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.root)
        )
    }

    @Unroll
    def "can detect metadata of #displayName"() {
        given:
        def execHandleFactory = createExecHandleFactory(systemProperties)

        when:
        def detector = createDefaultJvmMetadataDetector(execHandleFactory)
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
        def metadata = detector.getMetadata(javaHome)

        then:
        assert metadata.languageVersion == javaVersion
        assert displayName == null || displayName == metadata.displayName + " " + metadata.languageVersion.majorVersion
        assert metadata.javaHome != null

        where:
        jdk              | systemProperties       | javaVersion             | displayName                | exists | jre
        'localGradle'    | currentGradle()        | JavaVersion.current()   | null                       | true   | false
        'localGradle'    | currentGradle()        | JavaVersion.current()   | null                       | true   | true
        'openJdk4'       | openJdkJvm('4')        | JavaVersion.VERSION_1_4 | 'OpenJDK 4'                | true   | false
        'openJdk5'       | openJdkJvm('5')        | JavaVersion.VERSION_1_5 | 'OpenJDK 5'                | true   | false
        'openJdk6'       | openJdkJvm('6')        | JavaVersion.VERSION_1_6 | 'OpenJDK 6'                | true   | false
        'openJdk7'       | openJdkJvm('7')        | JavaVersion.VERSION_1_7 | 'OpenJDK 7'                | true   | false
        'openJdk8'       | openJdkJvm('8')        | JavaVersion.VERSION_1_8 | 'OpenJDK 8'                | true   | false
        'openJdk9'       | openJdkJvm('9')        | JavaVersion.VERSION_1_9 | 'OpenJDK 9'                | true   | false
        'openJdk9'       | openJdkJvm('9')        | JavaVersion.VERSION_1_9 | 'OpenJDK JRE 9'            | true   | true
        'AdoptOpenJDK11' | adoptOpenJDK('11.0.3') | JavaVersion.VERSION_11  | 'AdoptOpenJDK 11'          | true   | false
        'AdoptOpenJDK11' | adoptOpenJDK('11.0.3') | JavaVersion.VERSION_11  | 'AdoptOpenJDK JRE 11'      | true   | true
        'AdoptOpenJDKJ9' | adoptOpenJDKJ9('14.0.2') | JavaVersion.VERSION_14  | 'AdoptOpenJDK 14'      | true   | false
        'oracleJdk4'     | oracleJvm('4')         | JavaVersion.VERSION_1_4 | 'Oracle JDK 4'             | true   | false
        'oracleJre4'     | oracleJvm('4')         | JavaVersion.VERSION_1_4 | 'Oracle JRE 4'             | true   | true
        'oracleJdk5'     | oracleJvm('5')         | JavaVersion.VERSION_1_5 | 'Oracle JDK 5'             | true   | false
        'oracleJdk6'     | oracleJvm('6')         | JavaVersion.VERSION_1_6 | 'Oracle JDK 6'             | true   | false
        'oracleJdk7'     | oracleJvm('7')         | JavaVersion.VERSION_1_7 | 'Oracle JDK 7'             | true   | false
        'oracleJdk8'     | oracleJvm('8')         | JavaVersion.VERSION_1_8 | 'Oracle JDK 8'             | true   | false
        'oracleJdk9'     | oracleJvm('9')         | JavaVersion.VERSION_1_9 | 'Oracle JDK 9'             | true   | false
        'oracleJre9'     | oracleJvm('9')         | JavaVersion.VERSION_1_9 | 'Oracle JRE 9'             | true   | true
        'ibmJdk4'        | ibmJvm('4')            | JavaVersion.VERSION_1_4 | 'IBM JDK 4'                | true   | false
        'ibmJre4'        | ibmJvm('4')            | JavaVersion.VERSION_1_4 | 'IBM JRE 4'                | true   | true
        'ibmJdk5'        | ibmJvm('5')            | JavaVersion.VERSION_1_5 | 'IBM JDK 5'                | true   | false
        'ibmJdk6'        | ibmJvm('6')            | JavaVersion.VERSION_1_6 | 'IBM JDK 6'                | true   | false
        'ibmJdk7'        | ibmJvm('7')            | JavaVersion.VERSION_1_7 | 'IBM JDK 7'                | true   | false
        'ibmJdk8'        | ibmJvm('8')            | JavaVersion.VERSION_1_8 | 'IBM JDK 8'                | true   | false
        'ibmJdk9'        | ibmJvm('9')            | JavaVersion.VERSION_1_9 | 'IBM JDK 9'                | true   | false
        'zuluJre6'       | zuluJvm('6')           | JavaVersion.VERSION_1_6 | 'Zulu JRE 6'               | true   | true
        'zuluJdk8'       | zuluJvm('8')           | JavaVersion.VERSION_1_8 | 'Zulu JDK 8'               | true   | false
        'hpuxJre6'       | hpuxJvm('6')           | JavaVersion.VERSION_1_6 | 'HP-UX JRE 6'              | true   | true
        'hpuxJdk7'       | hpuxJvm('7')           | JavaVersion.VERSION_1_7 | 'HP-UX JDK 7'              | true   | false
        'sapjdk13'       | sapJvm('13')           | JavaVersion.VERSION_13  | 'SAP SapMachine JDK 13'    | true   | false
        'sapjre13'       | sapJvm('13')           | JavaVersion.VERSION_13  | 'SAP SapMachine JRE 13'    | true   | true
        'correttojdk11'  | correttoJvm('11')      | JavaVersion.VERSION_11  | 'Amazon Corretto JDK 11'   | true   | false
        'correttojre11'  | correttoJvm('11')      | JavaVersion.VERSION_11  | 'Amazon Corretto JRE 11'   | true   | true
        'bellsoftjdk11'  | bellsoftJvm('15')      | JavaVersion.VERSION_15  | 'BellSoft Liberica JDK 15' | true   | false
        'bellsoftjre11'  | bellsoftJvm('15')      | JavaVersion.VERSION_15  | 'BellSoft Liberica JRE 15' | true   | true
        'whitespaces'    | whitespaces('11.0.3')  | JavaVersion.VERSION_11  | 'AdoptOpenJDK JRE 11'      | true   | true
    }

    @Unroll
    def "probes whether #jdk is a j9 virtual machine"() {
        given:
        def execHandleFactory = createExecHandleFactory(systemProperties)

        when:
        def detector = createDefaultJvmMetadataDetector(execHandleFactory)
        def javaHome = temporaryFolder.newFolder(jdk)
        def metadata = detector.getMetadata(javaHome)

        then:
        assert metadata.hasCapability(JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE) == isJ9

        where:
        jdk              | systemProperties         | isJ9
        'openJdk9'       | openJdkJvm('9')          | false
        'AdoptOpenJDK11' | adoptOpenJDK('11.0.3')   | false
        'AdoptOpenJDKJ9' | adoptOpenJDKJ9('14.0.2') | true
        'oracleJdk4'     | oracleJvm('4')           | false
        'ibmJdk4'        | ibmJvm('4')              | true
        'ibmJre4'        | ibmJvm('4')              | true
        'ibmJdk5'        | ibmJvm('5')              | true
        'ibmJdk6'        | ibmJvm('6')              | true
        'ibmJdk7'        | ibmJvm('7')              | true
        'ibmJdk8'        | ibmJvm('8')              | true
        'ibmJdk9'        | ibmJvm('9')              | true
    }

    @Unroll
    def "detects invalid installation because #errorMessage"() {
        given:
        def execHandleFactory = createExecHandleFactory(systemProperties)

        when:
        def detector = createDefaultJvmMetadataDetector(execHandleFactory)
        File javaHome = new File(jdk)
        if (exists) {
            javaHome = temporaryFolder.newFolder(jdk)
        }
        def metadata = detector.getMetadata(javaHome)

        then:
        metadata.getErrorMessage().startsWith(errorMessage)
        assert metadata.getJavaHome() == javaHome.toPath()

        assertIsUnsupported({ metadata.languageVersion })
        assertIsUnsupported({ metadata.vendor })
        assertIsUnsupported({ metadata.implementationVersion })

        where:
        jdk                                   | systemProperties | exists | errorMessage
        'localGradle'                         | currentGradle()  | false  | "No such directory: "
        'binary that has invalid output'      | invalidOutput()  | true   | "Unexpected command output:"
        'binary that returns unknown version' | invalidVersion() | true   |  "Cannot parse version number: bad luck"
    }



    private static Map<String, String> invalidOutput() {
        // emulates a binary that would not return the correct number of outputs
        [:]
    }

    private static Map<String, String> invalidVersion() {
        // emulates a valid binary that would not return the correct version number
        ['java.home': "java-home",
         'java.version': "bad luck",
         'java.vendor': "Oracle Corporation",
         'os.arch': "amd64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "25.40-b25",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }


    private static Map<String, String> currentGradle() {
        ['java.home', 'java.version', 'java.vendor', 'os.arch', 'java.vm.name', 'java.vm.version', 'java.runtime.name'].collectEntries { [it, System.getProperty(it)] }
    }

    private static Map<String, String> openJdkJvm(String version) {
        ['java.home': "java-home",
         'java.version': "1.${version}.0",
         'java.vendor': "Oracle Corporation",
         'os.arch': "amd64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "25.40-b25",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

    private static Map<String, String> adoptOpenJDK(String version) {
        ['java.home': "java-home",
         'java.version': version,
         'java.vendor': "AdoptOpenJDK",
         'os.arch': "x86_64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "${version}+7",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    private static Map<String, String> adoptOpenJDKJ9(String version) {
        ['java.home': "java-home",
         'java.version': version,
         'java.vendor': "AdoptOpenJDK",
         'os.arch': "x86_64",
         'java.vm.name': "Eclipse OpenJ9 VM",
         'java.vm.version': "openj9-0.21.0",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    private static Map<String, String> whitespaces(String version) {
        ['java.home': "home-with-whitespaces\r",
         'java.version': version,
         'java.vendor': "AdoptOpenJDK\r",
         'os.arch': "x86_64\r",
         'java.vm.name': "OpenJDK 64-Bit Server VM\r",
         'java.vm.version': "${version}+7\r",
         'java.runtime.name': "OpenJDK Runtime Environment\r"
        ]
    }

    private static Map<String, String> oracleJvm(String version) {
        ['java.home': "java-home",
         'java.version': "1.${version}.0",
         'java.vendor': "Oracle Corporation",
         'os.arch': "amd64",
         'java.vm.name': "Java HotSpot(TM) 64-Bit Server VM",
         'java.vm.version': "25.40-b25",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

    private static Map<String, String> ibmJvm(String version) {
        ['java.home': "java-home",
         'java.version': "1.${version}.0",
         'java.vendor': "IBM Corporation",
         'os.arch': "amd64",
         'java.vm.name': "IBM J9 VM",
         'java.vm.version': "2.4",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

    private static Map<String, String> zuluJvm(String version) {
        ['java.home': "java-home",
         'java.version': "1.${version}.0_66",
         'java.vendor': "Azul Systems, Inc.",
         'os.arch': "amd64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "25.66-b17",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    private static Map<String, String> hpuxJvm(String version) {
        ['java.home': "java-home",
         'java.version': "1.${version}.0_66",
         'java.vendor': "Hewlett-Packard Co.",
         'os.arch': "ia64",
         'java.vm.name': "Java HotSpot(TM) 64-Bit Server VM",
         'java.vm.version': "25.66-b17",
         'java.runtime.name': "Java(TM) SE Runtime Environment"
        ]
    }

    private static Map<String, String> sapJvm(String version) {
        ['java.home': "java-home",
         'java.version': "${version}.0.2",
         'java.vendor': "SAP SE",
         'os.arch': "x86_64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "13.0.2+8-sapmachine",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    private static Map<String, String> correttoJvm(String version) {
        ['java.home': "java-home",
         'java.version': "${version}.0.8",
         'java.vendor': "Amazon.com Inc.",
         'os.arch': "x86_64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "11.0.8+10-LTS",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    private static Map<String, String> bellsoftJvm(String version) {
        ['java.home': "java-home",
         'java.version': "${version}",
         'java.vendor': "BellSoft.",
         'os.arch': "x86_64",
         'java.vm.name': "OpenJDK 64-Bit Server VM",
         'java.vm.version': "${version}+36",
         'java.runtime.name': "OpenJDK Runtime Environment"
        ]
    }

    def createExecHandleFactory(Map<String, String> actualProperties) {
        def execHandleFactory = Mock(ExecHandleFactory)
        def exec = Mock(ExecHandleBuilder)
        execHandleFactory.newExec() >> exec
        PrintStream output
        exec.setStandardOutput(_ as OutputStream) >> { OutputStream outputStream ->
            output = new PrintStream(outputStream)
            null
        }
        def handle = Mock(ExecHandle)
        handle.start() >> handle
        handle.waitForFinish() >> {
            actualProperties.each {
                output.println(it.value)
            }
            Mock(ExecResult)
        }
        exec.build() >> handle
        execHandleFactory
    }

    void assertIsUnsupported(Closure<?> unsupportedOperation) {
        try {
            unsupportedOperation.run()
            throw new SpockAssertionError("Expected to throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }

    }
}
