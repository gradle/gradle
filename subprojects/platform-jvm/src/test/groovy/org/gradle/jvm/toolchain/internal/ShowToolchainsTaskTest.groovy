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


import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.jvm.toolchain.internal.task.ShowToolchainsTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class ShowToolchainsTaskTest extends AbstractProjectBuilderSpec {

    TestShowToolchainsTask task
    def output
    JvmMetadataDetector detector

    def setup() {
        task = project.tasks.create("test", TestShowToolchainsTask.class)
        detector = Mock(JvmMetadataDetector)
        output = new TestStyledTextOutput()

        task.metadataDetector = detector
        task.providerFactory = createProviderFactory(true, true)
        task.outputFactory = Mock(StyledTextOutputFactory) {
            create(_ as Class) >> output
        }
    }

    def "reports toolchains in right order"() {
        def jdk14 = testLocation("14")
        def jdk15 = testLocation("15")
        def jdk9 = testLocation("9")
        def jdk8 = testLocation("1.8.0_202")
        def jdk82 = testLocation("1.8.0_404")

        given:
        task.installationRegistry = createRegistry([jdk14, jdk15, jdk9, jdk8, jdk82])
        detector.getMetadata(jdk14) >> metadata("14", "+2")
        detector.getMetadata(jdk15) >> metadata("15-ea", "+2")
        detector.getMetadata(jdk9) >> metadata("9", "+2")
        detector.getMetadata(jdk8) >> metadata("1.8.0_202", "-b01")
        detector.getMetadata(jdk82) >> metadata("1.8.0_404", "-b01")

        when:
        task.showToolchains()

        then:
        output.value == """
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
        def jdk14 = testLocation("14")
        def invalid = testLocation("invalid")
        def noSuchDirectory = testLocation("noSuchDirectory")

        given:
        task.installationRegistry = createRegistry([jdk14, invalid, noSuchDirectory])
        detector.getMetadata(jdk14) >> metadata("14", "+1")
        detector.getMetadata(invalid) >> newInvalidMetadata()
        detector.getMetadata(noSuchDirectory) >> newInvalidMetadata()

        when:
        task.showToolchains()

        then:
        output.value == """
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
        def path = testLocation("path")
        def createFailureWithNCauses = { n ->
            def rootCause = new Exception("lastLine")
            n == 0
                ? rootCause :
                (1..n).inject(new Exception("lastLine")) { acc, it -> new Exception("line${n - it}", acc) }
        }

        given:
        task.installationRegistry = createRegistry([path])
        detector.getMetadata(path) >> JvmInstallationMetadata.failure(path.location, createFailureWithNCauses(nCauses))

        when:
        task.showToolchains()

        then:
        output.value == """
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
        task.providerFactory = createProviderFactory(false, false)
        task.installationRegistry = createRegistry([])

        when:
        task.showToolchains()

        then:
        output.value == """
{identifier} + Options{normal}
     | Auto-detection:     {description}Disabled{normal}
     | Auto-download:      {description}Disabled{normal}

"""
    }

    def "reports only toolchains with errors"() {
        def invalid = testLocation("invalid")
        def noSuchDirectory = testLocation("noSuchDirectory")

        given:
        task.installationRegistry = createRegistry([invalid, noSuchDirectory])
        detector.getMetadata(invalid) >> newInvalidMetadata()
        detector.getMetadata(noSuchDirectory) >> newInvalidMetadata()

        when:
        task.showToolchains()

        then:
        output.value == """
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

    JvmInstallationMetadata metadata(String javaVersion, String build) {
        def runtimeVersion = javaVersion + build
        def jvmVersion = runtimeVersion + "-vm"
        return JvmInstallationMetadata.from(new File("path"), javaVersion, "adoptopenjdk", "runtimeName", runtimeVersion, "", jvmVersion, "jvmVendor", "archName")
    }

    JvmInstallationMetadata newInvalidMetadata() {
        return JvmInstallationMetadata.failure(new File("path"), "errorMessage")
    }

    ProviderFactory createProviderFactory(boolean enableDetection, boolean enableDownload) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.of(String.valueOf(enableDetection))
        providerFactory.gradleProperty("org.gradle.java.installations.auto-download") >> Providers.of(String.valueOf(enableDownload))
        providerFactory
    }

    private JavaInstallationRegistry createRegistry(List<InstallationLocation> locations) {
        return new JavaInstallationRegistry(null, detector, null, null, new NoOpProgressLoggerFactory()) {
            @Override
            protected Set<InstallationLocation> listInstallations() {
                return locations;
            }
        }
    }

    private static InstallationLocation testLocation(String javaHomePath) {
        return new InstallationLocation(new File(javaHomePath), "TestSource");
    }

    static class TestShowToolchainsTask extends ShowToolchainsTask {
        def installationRegistry
        def metadataDetector
        def outputFactory
        def providerFactory

        @Override
        protected JavaInstallationRegistry getInstallationRegistry() {
            installationRegistry
        }

        @Override
        protected StyledTextOutputFactory getTextOutputFactory() {
            outputFactory
        }

        @Override
        protected ProviderFactory getProviderFactory() {
            providerFactory
        }
    }
}
