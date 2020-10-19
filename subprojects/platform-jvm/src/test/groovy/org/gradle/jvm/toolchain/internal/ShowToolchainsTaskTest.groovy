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
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.testfixtures.ProjectBuilder

import java.nio.file.Paths

class ShowToolchainsTaskTest extends AbstractProjectBuilderSpec {

    def task
    def output

    def setup() {
        task = project.tasks.create("test", TestShowToolchainsTask.class)
        task.installationRegistry = Mock(SharedJavaInstallationRegistry)
        task.probeService = Mock(JavaInstallationProbe)
        output = new TestStyledTextOutput()
        task.getRenderer().output = output
    }

    def "reports toolchains in right order"() {
        def jdk14 = new File("14")
        def jdk15 = new File("15")
        def jdk9 = new File("9")
        def jdk8 = new File("1.8.0_202")
        def jdk82 = new File("1.8.0_404")

        given:
        task.installationRegistry.listInstallations() >> [jdk14, jdk15, jdk9, jdk8, jdk82].collect {new InstallationLocation(it, "TestSource")}
        task.probeService.checkJdk(jdk14) >> newProbe("14", JavaVersion.VERSION_14)
        task.probeService.checkJdk(jdk15) >> newProbe("15-ea", JavaVersion.VERSION_15)
        task.probeService.checkJdk(jdk9) >> newProbe("9", JavaVersion.VERSION_1_9)
        task.probeService.checkJdk(jdk8) >> newProbe("1.8.0_202", JavaVersion.VERSION_1_8)
        task.probeService.checkJdk(jdk82) >> newProbe("1.8.0_404", JavaVersion.VERSION_1_8)

        when:
        def project = ProjectBuilder.builder().build()
        task.generate(project)

        then:
        output.toString() == """{identifier} + name 1.8.0_202{normal}
     | Location:           {description}/path{normal}
     | Language Version:   {description}8{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + name 1.8.0_404{normal}
     | Location:           {description}/path{normal}
     | Language Version:   {description}8{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + name 9{normal}
     | Location:           {description}/path{normal}
     | Language Version:   {description}9{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + name 14{normal}
     | Location:           {description}/path{normal}
     | Language Version:   {description}14{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

{identifier} + name 15-ea{normal}
     | Location:           {description}/path{normal}
     | Language Version:   {description}15{normal}
     | Is JDK:             {description}false{normal}
     | Detected by:        {description}TestSource{normal}

"""
    }

    private JavaInstallationProbe.ProbeResult newProbe(String implVersion, JavaVersion languageVersion) {
        JavaInstallationProbe.ProbeResult probe = Mock(JavaInstallationProbe.ProbeResult)
        probe.implementationName >> "name"
        probe.implementationJavaVersion >> implVersion
        probe.javaHome >> Paths.get("/path")
        probe.javaVersion >> languageVersion
        probe
    }

    static class TestShowToolchainsTask extends ShowToolchainsTask {
        def installationRegistry
        def probeService

        @Override
        protected SharedJavaInstallationRegistry getInstallationRegistry() {
            return installationRegistry
        }

        @Override
        protected JavaInstallationProbe getProbeService() {
            return probeService
        }

    }
}
