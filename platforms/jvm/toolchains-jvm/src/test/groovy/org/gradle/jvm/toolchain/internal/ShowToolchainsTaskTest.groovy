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


import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.jvm.toolchain.internal.task.ShowToolchainsTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Subject

import javax.inject.Inject

class ShowToolchainsTaskTest extends AbstractProjectBuilderSpec {
    @Subject
    TestShowToolchainsTask task
    TestStyledTextOutput outputProbe = new TestStyledTextOutput()
    ToolchainConfiguration toolchainConfiguration = Stub(ToolchainConfiguration)

    def defineJdks(JvmToolchainMetadata... toolchains) {
        def javaInstallationRegistry = Mock(JavaInstallationRegistry)
        javaInstallationRegistry.toolchains() >> toolchains

        def styledTextOutputFactory = Mock(StyledTextOutputFactory)
        styledTextOutputFactory.create(_) >> outputProbe

        task = project.tasks.create("test", TestShowToolchainsTask.class, javaInstallationRegistry, styledTextOutputFactory, toolchainConfiguration)
    }

    def "reports toolchains in right order"() {
        given:
        defineJdks(
            jdk("14", "+2", "14"),
            jdk("15-ea", "+2", "15"),
            jdk("9", "+2", "15"),
            jdk("1.8.0_202", "-b01", "1.8.0_202"),
            jdk("1.8.0_404", "-b01", "1.8.0_404")
        )
        toolchainConfiguration.isAutoDetectEnabled() >> true
        toolchainConfiguration.isDownloadEnabled() >> true

        when:
        task.showToolchains()

        then:
        outputProbe.value == """
{identifier} + Options{normal}
     | Auto-detection:     {description}Enabled{normal}
     | Auto-download:      {description}Enabled{normal}

{identifier} + AdoptOpenJDK JRE 1.8.0_202-b01{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}archName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 1.8.0_404-b01{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}archName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 9+2{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}9{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}archName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 14+2{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}14{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}archName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 15-ea+2{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}15{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}archName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

"""
    }

    def "reports toolchains with good and invalid ones"() {
        given:
        defineJdks(
            jdk("14", "+1", "14"),
            jdk(null, null, "invalid"),
            jdk(null, null, "noSuchDirectory"),
        )
        toolchainConfiguration.isAutoDetectEnabled() >> true
        toolchainConfiguration.isDownloadEnabled() >> true

        when:
        task.showToolchains()

        then:
        outputProbe.value == """
{identifier} + Options{normal}
     | Auto-detection:     {description}Enabled{normal}
     | Auto-download:      {description}Enabled{normal}

{identifier} + AdoptOpenJDK JRE 14+1{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}14{normal}
     | Vendor:             {description}AdoptOpenJDK{normal}
     | Architecture:       {description}archName{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + Invalid toolchains{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}

"""
    }

    def "reports toolchain probing failure cause lines"() {
        given:
        def createFailureWithNCauses = { n ->
            def rootCause = new Exception("lastLine")
            n == 0
                ? rootCause :
                (1..n).inject(new Exception("lastLine")) { acc, it -> new Exception("line${n - it}", acc) }
        }

        def jdkPath = testLocation("path")
        defineJdks(
            new JvmToolchainMetadata(JvmInstallationMetadata.failure(jdkPath.location, createFailureWithNCauses(nCauses)), jdkPath),
        )
        toolchainConfiguration.isAutoDetectEnabled() >> true
        toolchainConfiguration.isDownloadEnabled() >> true

        when:
        task.showToolchains()

        then:
        outputProbe.value == """
{identifier} + Options{normal}
     | Auto-detection:     {description}Enabled{normal}
     | Auto-download:      {description}Enabled{normal}

{identifier} + Invalid toolchains{normal}
{identifier}     + path{normal}
$errorLines

"""

        where:
        nCauses << [0, 1, 5, 6, 7]
        errorLines << [
            // 0
            "       | Error:              {description}lastLine{normal}",

            // 1
            """       | Error:              {description}line0{normal}
       |     Caused by:      {description}lastLine{normal}""",

            // 5
            """       | Error:              {description}line0{normal}
       |     Caused by:      {description}line1{normal}
       |     Caused by:      {description}line2{normal}
       |     Caused by:      {description}line3{normal}
       |     Caused by:      {description}line4{normal}
       |     Caused by:      {description}lastLine{normal}""",

            // 6
            """       | Error:              {description}line0{normal}
       |     Caused by:      {description}line1{normal}
       |     Caused by:      {description}line2{normal}
       |     Caused by:      {description}line3{normal}
       |     Caused by:      {description}line4{normal}
       |     Caused by:      {description}line5{normal}
       |                     {description}...{normal}""",

            // 7
            """       | Error:              {description}line0{normal}
       |     Caused by:      {description}line1{normal}
       |     Caused by:      {description}line2{normal}
       |     Caused by:      {description}line3{normal}
       |     Caused by:      {description}line4{normal}
       |     Caused by:      {description}line5{normal}
       |                     {description}...{normal}"""
        ]
    }

    def "reports download and detection options"() {
        given:
        defineJdks()
        toolchainConfiguration.isAutoDetectEnabled() >> false
        toolchainConfiguration.isDownloadEnabled() >> false

        when:
        task.showToolchains()

        then:
        outputProbe.value == """
{identifier} + Options{normal}
     | Auto-detection:     {description}Disabled{normal}
     | Auto-download:      {description}Disabled{normal}

"""
    }

    def "reports only toolchains with errors"() {
        given:
        defineJdks(
            jdk(null, null, "invalid"),
            jdk(null, null, "noSuchDirectory"),
        )
        toolchainConfiguration.isAutoDetectEnabled() >> true
        toolchainConfiguration.isDownloadEnabled() >> true

        when:
        task.showToolchains()

        then:
        outputProbe.value == """
{identifier} + Options{normal}
     | Auto-detection:     {description}Enabled{normal}
     | Auto-download:      {description}Enabled{normal}

{identifier} + Invalid toolchains{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}

"""
    }

    private static JvmToolchainMetadata jdk(String javaVersion, String build, String javaHomePath) {
        if (javaVersion == null) {
            return new JvmToolchainMetadata(newInvalidMetadata(), testLocation(javaHomePath))
        }
        return new JvmToolchainMetadata(metadata(javaVersion, build), testLocation(javaHomePath))
    }

    private static JvmInstallationMetadata metadata(String javaVersion, String build) {
        def runtimeVersion = javaVersion + build
        def jvmVersion = runtimeVersion + "-vm"
        return JvmInstallationMetadata.from(new File("path"), javaVersion, "adoptopenjdk", "runtimeName", runtimeVersion, "", jvmVersion, "jvmVendor", "archName")
    }

    private static JvmInstallationMetadata newInvalidMetadata() {
        return JvmInstallationMetadata.failure(new File("path"), "errorMessage")
    }

    private static InstallationLocation testLocation(String javaHomePath) {
        return InstallationLocation.userDefined(new File(javaHomePath), "TestSource")
    }

    static abstract class TestShowToolchainsTask extends ShowToolchainsTask {
        private final JavaInstallationRegistry installationRegistry
        private final StyledTextOutputFactory outputFactory
        private final ToolchainConfiguration toolchainConfiguration

        @Inject
        TestShowToolchainsTask(JavaInstallationRegistry installationRegistry, StyledTextOutputFactory outputFactory, ToolchainConfiguration toolchainConfiguration) {
            this.toolchainConfiguration = toolchainConfiguration
            this.installationRegistry = installationRegistry
            this.outputFactory = outputFactory
        }

        @Override
        protected JavaInstallationRegistry getInstallationRegistry() {
            installationRegistry
        }

        @Override
        protected StyledTextOutputFactory getTextOutputFactory() {
            outputFactory
        }

        @Override
        protected ToolchainConfiguration getToolchainConfiguration() {
            return toolchainConfiguration
        }
    }
}
