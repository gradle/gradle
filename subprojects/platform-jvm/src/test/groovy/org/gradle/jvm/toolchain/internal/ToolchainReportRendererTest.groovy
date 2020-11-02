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

import org.gradle.api.JavaVersion
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.jvm.toolchain.internal.task.ReportableToolchain
import org.gradle.jvm.toolchain.internal.task.ToolchainReportRenderer
import spock.lang.Specification

import java.nio.file.Paths

class ToolchainReportRendererTest extends Specification {

    JavaInstallationProbe.ProbeResult probe = Mock(JavaInstallationProbe.ProbeResult)
    InstallationLocation installation = Mock(InstallationLocation)

    def "jre is rendered properly"() {
        given:
        probe.implementationName >> "toolchainName"
        probe.implementationJavaVersion >> "1.8.0"
        probe.javaHome >> Paths.get("path")
        probe.javaVersion >> JavaVersion.VERSION_1_8
        installation.source >> "SourceSupplier"

        expect:
        assertOutput("""{identifier} + toolchainName 1.8.0{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}SourceSupplier{normal}

""")
    }

    def "jdk is rendered properly"() {
        given:
        probe.implementationName >> "toolchainName"
        probe.implementationJavaVersion >> "1.8.0"
        probe.javaHome >> Paths.get("path")
        probe.installType >> JavaInstallationProbe.InstallType.IS_JDK
        probe.javaVersion >> JavaVersion.VERSION_1_8
        installation.source >> "SourceSupplier"

        expect:
        assertOutput("""{identifier} + toolchainName 1.8.0{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Is JDK:             {description}true{normal}
     | Detected by:        {description}SourceSupplier{normal}

""")
    }

    void assertOutput(String expectedOutput) {
        def renderer = new ToolchainReportRenderer()
        def output = new TestStyledTextOutput()
        renderer.output = output
        renderer.printToolchain(new ReportableToolchain(probe, installation))
        assert output.value == expectedOutput
    }
}
