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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.tasks.diagnostics.internal.ToolchainReportRenderer
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ToolchainReportRendererTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    InstallationLocation installation = Mock(InstallationLocation)

    def "jre is rendered properly"() {
        given:
        def metadata = JvmInstallationMetadata.from(
            new File("path"),
            "1.8.0", "vendorName",
            "runtimeName", "1.8.0-b01",
            "jvmName", "25.292-b01", "jvmVendor",
            "myArch"
        )
        installation.source >> "SourceSupplier"

        expect:
        assertOutput(metadata, """{identifier} + vendorName JRE 8 (1.8.0-b01){normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Vendor:             {description}vendorName{normal}
     | Architecture:       {description}myArch{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}SourceSupplier{normal}

""")
    }

    def "jdk is rendered properly"() {
        given:
        def javaHome = temporaryFolder.createDir("javahome")
        def metadata = JvmInstallationMetadata.from(
                javaHome,
                "1.8.0", "adoptopenjdk",
                "runtimeName", "1.8.0-b01",
                "jvmName", "25.292-b01", "jvmVendor",
                "myArch"
            )
        installation.source >> "SourceSupplier"

        def binDir = javaHome.createDir("bin")
        binDir.file(OperatingSystem.current().getExecutableName('javac')).touch()
        binDir.file(OperatingSystem.current().getExecutableName('javadoc')).touch()
        binDir.file(OperatingSystem.current().getExecutableName('jar')).touch()

        expect:
        assertOutput(metadata, """{identifier} + AdoptOpenJDK JDK 8 (1.8.0-b01){normal}
     | Location:           {description}${javaHome}{normal}
     | Language Version:   {description}8{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}myArch{normal}
     | Is JDK:             {description}true{normal}
     | Detected by:        {description}SourceSupplier{normal}

""")
    }

    void assertOutput(JvmInstallationMetadata metadata, String expectedOutput) {
        def renderer = new ToolchainReportRenderer()
        def output = new TestStyledTextOutput()
        renderer.output = output
        renderer.printDetectedToolchain(new JvmToolchainMetadata(metadata, installation))
        assert output.value == expectedOutput
    }
}
