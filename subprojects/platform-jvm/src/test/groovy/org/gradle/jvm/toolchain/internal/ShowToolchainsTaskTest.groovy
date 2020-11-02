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

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.jvm.toolchain.internal.task.ShowToolchainsTask
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.jvm.toolchain.internal.JavaInstallationProbe.InstallType

class ShowToolchainsTaskTest extends AbstractProjectBuilderSpec {

    TestShowToolchainsTask task
    def output
    JvmMetadataDetector detector

    def setup() {
        task = project.tasks.create("test", TestShowToolchainsTask.class)
        task.installationRegistry = Mock(SharedJavaInstallationRegistry)
        detector = Mock(JvmMetadataDetector)
        task.metadataDetector = detector
        output = new TestStyledTextOutput()
        task.outputFactory = Mock(StyledTextOutputFactory) {
            create(_ as Class) >> output
        }
    }

    def "reports toolchains in right order"() {
        def jdk14 = new File("14")
        def jdk15 = new File("15")
        def jdk9 = new File("9")
        def jdk8 = new File("1.8.0_202")
        def jdk82 = new File("1.8.0_404")

        given:
        task.installationRegistry.listInstallations() >>
            [jdk14, jdk15, jdk9, jdk8, jdk82].collect {new InstallationLocation(it, "TestSource")}
        detector.getMetadata(jdk14) >> metadata("14")
        detector.getMetadata(jdk15) >> metadata("15-ea")
        detector.getMetadata(jdk9) >> metadata("9")
        detector.getMetadata(jdk8) >> metadata("1.8.0_202")
        detector.getMetadata(jdk82) >> metadata("1.8.0_404")

        when:
        task.showToolchains()

        then:
        output.value == """
{identifier} + AdoptOpenJDK JRE 1.8.0_202{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 1.8.0_404{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}8{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 9{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}9{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 14{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}14{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + AdoptOpenJDK JRE 15-ea{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}15{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

"""
    }

    def "reports toolchains with good and invalid ones"() {
        def jdk14 = new File("14")
        def invalid = new File("invalid")
        def noSuchDirectory = new File("noSuchDirectory")

        given:
        task.installationRegistry.listInstallations() >>
            [jdk14, invalid, noSuchDirectory].collect {new InstallationLocation(it, "TestSource")}
        detector.getMetadata(jdk14) >> metadata("14")
        detector.getMetadata(invalid) >> newInvalidMetadata(InstallType.INVALID_JDK)
        detector.getMetadata(noSuchDirectory) >> newInvalidMetadata(InstallType.NO_SUCH_DIRECTORY)

        when:
        task.showToolchains()

        then:
        output.value == """
{identifier} + AdoptOpenJDK JRE 14{normal}
     | Location:           {description}path{normal}
     | Language Version:   {description}14{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + Invalid toolchains{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}

"""
    }

    def "reports only toolchains with errors"() {
        def invalid = new File("invalid")
        def noSuchDirectory = new File("noSuchDirectory")

        given:
        task.installationRegistry.listInstallations() >> [
            invalid, noSuchDirectory].collect {new InstallationLocation(it, "TestSource")}
        detector.getMetadata(invalid) >> newInvalidMetadata(InstallType.INVALID_JDK)
        detector.getMetadata(noSuchDirectory) >> newInvalidMetadata(InstallType.NO_SUCH_DIRECTORY)

        when:
        task.showToolchains()

        then:
        output.value == """
{identifier} + Invalid toolchains{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}
{identifier}     + path{normal}
       | Error:              {description}errorMessage{normal}

"""
    }

    JvmInstallationMetadata metadata(String implVersion) {
        return JvmInstallationMetadata.from(new File("path"), implVersion, "adoptopenjdk", "")
    }

    JvmInstallationMetadata newInvalidMetadata(InstallType type) {
        return JvmInstallationMetadata.failure(new File("path"), "errorMessage")
    }

    static class TestShowToolchainsTask extends ShowToolchainsTask {
        def installationRegistry
        def metadataDetector
        def outputFactory

        @Override
        protected SharedJavaInstallationRegistry getInstallationRegistry() {
            installationRegistry
        }

        @Override
        protected JvmMetadataDetector getMetadataDetector() {
            return metadataDetector
        }

        @Override
        protected StyledTextOutputFactory getTextOutputFactory() {
            outputFactory
        }
    }
}
