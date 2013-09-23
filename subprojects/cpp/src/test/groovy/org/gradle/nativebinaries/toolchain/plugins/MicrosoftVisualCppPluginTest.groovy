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
import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.internal.nativeplatform.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.toolchain.VisualCpp
import org.gradle.test.fixtures.file.TestDirectoryProvider
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

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "installs an unavailable tool chain when not windows"() {
        when:
        project.plugins.apply(MicrosoftVisualCppPlugin)

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
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.toolChains.create("vc", VisualCpp)

        then:
        def visualCpp = project.toolChains.vc
        !visualCpp.availability.available
        visualCpp.availability.unavailableMessage == 'Visual Studio installation cannot be found'
        visualCpp.toString() == "ToolChain 'vc' (Visual C++)"

        cleanup:
        processEnvironment.setEnvironmentVariable(pathVar, originalPath)
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }
}
