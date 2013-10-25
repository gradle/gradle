/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.toolchain.plugins
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.internal.nativeplatform.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.internal.ToolChainAvailability
import org.gradle.nativebinaries.toolchain.VisualCpp
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualCppToolChain
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class MicrosoftVisualCppPluginTest extends Specification {
    def ProcessEnvironment processEnvironment = NativeServices.getInstance().get(ProcessEnvironment.class);
    def pathVar = OperatingSystem.current().getPathVar()
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    def project = TestUtil.createRootProject()

    def setup() {
        project.plugins.apply(MicrosoftVisualCppPlugin)
    }

    def "makes a VisualCpp tool chain available"() {
        when:
        project.toolChains.create("vc", VisualCpp)

        then:
        project.toolChains.vc instanceof VisualCppToolChain
    }

    @Requires([TestPrecondition.CAN_INSTALL_EXECUTABLE, TestPrecondition.CPP_TOOLCHAINS_AVAILABLE])
    def "registers default VisualCpp tool chain"() {
        when:
        project.toolChains.addDefaultToolChain()

        then:
        project.toolChains.visualCpp instanceof VisualCppToolChain
    }

    def "VisualCpp tool chain is extended"() {
        when:
        project.toolChains.create("vc", VisualCpp)

        then:
        with (project.toolChains.vc) {
            it instanceof ExtensionAware
            it.ext instanceof ExtraPropertiesExtension
        }
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "installs an unavailable tool chain when not windows"() {
        when:
        project.toolChains.create("vc", VisualCpp)

        then:
        def visualCpp = project.toolChains.vc
        !visualCpp.availability.available
        visualCpp.availability.unavailableMessage == 'Not available on this operating system.'
        visualCpp.toString() == "ToolChain 'vc' (Visual C++)"
    }

    @Requires(TestPrecondition.WINDOWS)
    def "installs an unavailable tool chain when on windows but Visual Studio install not located"() {
        given:
        def originalPath = System.getenv(pathVar)

        and:
        def dummyCompiler = file("dummy/cl.exe").createFile()
        processEnvironment.setEnvironmentVariable(pathVar, dummyCompiler.getParentFile().absolutePath);

        when:
        project.toolChains.create("vc", VisualCpp)

        then:
        ToolChainAvailability availability = project.toolChains.vc.availability
        !availability.available
        availability.unavailableMessage.startsWith 'Visual Studio installation cannot be located. Searched in ['

        cleanup:
        processEnvironment.setEnvironmentVariable(pathVar, originalPath)
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }
}
