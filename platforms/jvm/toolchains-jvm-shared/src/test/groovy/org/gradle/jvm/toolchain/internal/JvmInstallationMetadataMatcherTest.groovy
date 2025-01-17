/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector
import org.gradle.internal.jvm.inspection.MetadataProbe
import org.gradle.internal.jvm.inspection.ProbedSystemProperty
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.process.ExecResult
import org.gradle.process.internal.ClientExecHandleBuilder
import org.gradle.process.internal.ClientExecHandleBuilderFactory
import org.gradle.process.internal.ExecHandle
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.TempDir

class JvmInstallationMetadataMatcherTest extends Specification {

    @TempDir
    File temporaryFolder

    TestFile tmpDir
    def setup() {
        tmpDir = new TestFile(new File(temporaryFolder, "tmp").tap { mkdirs() })
    }

    def "ibm vendors match semeru runtime metadata (java version: #javaVersion, vendor: #vendor, implementation: #implementation)"() {
        given:
        def execHandleFactory = createExecHandleFactory(systemProperties)
        def detector = createDefaultJvmMetadataDetector(execHandleFactory)
        def javaHome = new File(temporaryFolder, jdk).tap { mkdirs() }
        def metadata = detector.getMetadata(testLocation(javaHome))

        when:
        def spec = TestUtil.objectFactory().newInstance(DefaultToolchainSpec)
        spec.getLanguageVersion().set(JavaLanguageVersion.of(javaVersion.getMajorVersion()))
        spec.getVendor().set(vendor)
        spec.getImplementation().set(implementation)

        then:
        new JvmInstallationMetadataMatcher(spec, Collections.emptySet()).test(metadata)

        where:
        jdk              | systemProperties         | javaVersion             | vendor                    | implementation
        'semeru11'       | semeruJvm11()            | JavaVersion.VERSION_11  | JvmVendorSpec.IBM         | JvmImplementation.VENDOR_SPECIFIC
        'semeru16'       | semeruJvm16()            | JavaVersion.VERSION_16  | JvmVendorSpec.IBM         | JvmImplementation.VENDOR_SPECIFIC
        'semeru17'       | semeruJvm17()            | JavaVersion.VERSION_17  | JvmVendorSpec.IBM         | JvmImplementation.VENDOR_SPECIFIC

        'semeru11'       | semeruJvm11()            | JavaVersion.VERSION_11  | JvmVendorSpec.IBM         | JvmImplementation.J9
        'semeru16'       | semeruJvm16()            | JavaVersion.VERSION_16  | JvmVendorSpec.IBM         | JvmImplementation.J9
        'semeru17'       | semeruJvm17()            | JavaVersion.VERSION_17  | JvmVendorSpec.IBM         | JvmImplementation.J9

        'semeru11'       | semeruJvm11()            | JavaVersion.VERSION_11  | JvmVendorSpec.IBM_SEMERU  | JvmImplementation.VENDOR_SPECIFIC
        'semeru16'       | semeruJvm16()            | JavaVersion.VERSION_16  | JvmVendorSpec.IBM_SEMERU  | JvmImplementation.VENDOR_SPECIFIC
        'semeru17'       | semeruJvm17()            | JavaVersion.VERSION_17  | JvmVendorSpec.IBM_SEMERU  | JvmImplementation.VENDOR_SPECIFIC

        'semeru11'       | semeruJvm11()            | JavaVersion.VERSION_11  | JvmVendorSpec.IBM_SEMERU  | JvmImplementation.J9
        'semeru16'       | semeruJvm16()            | JavaVersion.VERSION_16  | JvmVendorSpec.IBM_SEMERU  | JvmImplementation.J9
        'semeru17'       | semeruJvm17()            | JavaVersion.VERSION_17  | JvmVendorSpec.IBM_SEMERU  | JvmImplementation.J9
    }

    def createExecHandleFactory(Map<String, String> actualProperties) {
        def probedSystemProperties = ProbedSystemProperty.values().findAll { it != ProbedSystemProperty.Z_ERROR }
        if (!actualProperties.isEmpty()) {
            assert actualProperties.keySet() == probedSystemProperties.collect { it.systemPropertyKey }.toSet()
        }

        def execHandleFactory = Mock(ClientExecHandleBuilderFactory)
        def exec = Mock(ClientExecHandleBuilder)
        execHandleFactory.newExecHandleBuilder() >> exec
        PrintStream output
        exec.setStandardOutput(_ as OutputStream) >> { OutputStream outputStream ->
            output = new PrintStream(outputStream)
            null
        }
        def handle = Mock(ExecHandle)
        handle.start() >> handle
        handle.waitForFinish() >> {
            // important to output in the order of the enum members as parsing uses enum ordinals
            probedSystemProperties.each {
                def actualValue = actualProperties[it.systemPropertyKey]
                // write conditionally to simulate wrong number of outputs
                if (actualValue != null) {
                    output.println(MetadataProbe.MARKER_PREFIX + actualValue)
                }
            }
            Mock(ExecResult)
        }
        exec.build() >> handle
        execHandleFactory
    }

    private DefaultJvmMetadataDetector createDefaultJvmMetadataDetector(ClientExecHandleBuilderFactory execHandleFactory) {
        return new DefaultJvmMetadataDetector(
                execHandleFactory,
                TestFiles.tmpDirTemporaryFileProvider(tmpDir)
        )
    }

    private InstallationLocation testLocation(File javaHome) {
        InstallationLocation.userDefined(javaHome, "test")
    }

    private static Map<String, String> semeruJvm11() {
        ['java.home': "java-home",
         'java.version': "11.0.17",
         'java.vendor': "IBM Corporation",
         'os.arch': "x86_64",
         'java.vm.name': "Eclipse OpenJ9 VM",
         'java.vm.version': "openj9-0.35.0",
         'java.vm.vendor': "Eclipse OpenJ9",
         'java.runtime.name': "IBM Semeru Runtime Open Edition",
         'java.runtime.version': "11.0.17+8"
        ]
    }

    private static Map<String, String> semeruJvm16() {
        ['java.home': "java-home",
         'java.version': "16.0.2",
         'java.vendor': "International Business Machines Corporation",
         'os.arch': "x86_64",
         'java.vm.name': "Eclipse OpenJ9 VM",
         'java.vm.version': "openj9-0.27.0",
         'java.vm.vendor': "Eclipse OpenJ9",
         'java.runtime.name': "IBM Semeru Runtime Open Edition",
         'java.runtime.version': "16.0.2+7"
        ]
    }

    private static Map<String, String> semeruJvm17() {
        ['java.home': "java-home",
         'java.version': "17.0.5",
         'java.vendor': "IBM Corporation",
         'os.arch': "x86_64",
         'java.vm.name': "Eclipse OpenJ9 VM",
         'java.vm.version': "openj9-0.35.0",
         'java.vm.vendor': "Eclipse OpenJ9",
         'java.runtime.name': "IBM Semeru Runtime Open Edition",
         'java.runtime.version': "17.0.5+8"
        ]
    }

}
