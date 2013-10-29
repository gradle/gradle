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

package org.gradle.nativebinaries.toolchain.internal.msvcpp
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.internal.ToolChainAvailability
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualCppToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final ExecActionFactory execActionFactory = Mock(ExecActionFactory)
    final VisualStudioLocator.SearchResult visualStudio = Mock(VisualStudioLocator.SearchResult)
    final VisualStudioLocator.SearchResult windowsSdk = Mock(VisualStudioLocator.SearchResult)
    final candidate = file('test')
    final VisualStudioLocator visualStudioLocator = Stub(VisualStudioLocator) {
        locateDefaultVisualStudio() >> visualStudio
        locateDefaultWindowsSdk() >> windowsSdk
    }
    final OperatingSystem operatingSystem = Mock(OperatingSystem) {
        isWindows() >> true
    }
    final toolChain = new VisualCppToolChain("visualCpp", operatingSystem, fileResolver, execActionFactory, visualStudioLocator)


    def "uses .lib file for shared library at link time"() {
        given:
        operatingSystem.getSharedLibraryName("test") >> "test.dll"

        expect:
        toolChain.getSharedLibraryLinkFileName("test") == "test.lib"
    }

    def "uses .dll file for shared library at runtime time"() {
        given:
        operatingSystem.getSharedLibraryName("test") >> "test.dll"

        expect:
        toolChain.getSharedLibraryName("test") == "test.dll"
    }

    def "is unavailable when visual studio installation cannot be located"() {
        when:
        visualStudio.found >> false
        visualStudio.searchLocations >> [candidate]
        windowsSdk.found >> false

        and:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == "Visual Studio installation cannot be located. Searched in [${candidate}]."
    }

    def "is unavailable when windows SDK cannot be located"() {
        when:
        visualStudio.found >> true
        windowsSdk.found >> false
        windowsSdk.searchLocations >> [candidate]

        and:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability);

        then:
        !availability.available
        availability.unavailableMessage == "Windows SDK cannot be located. Searched in [${candidate}]."
    }

    def "is available when visual studio installation and windows SDK can be located"() {
        when:
        visualStudio.found >> true
        windowsSdk.found >> true

        and:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability);

        then:
        availability.available
    }

    def "uses provided installDir and windowsSdkDir for location"() {
        when:
        toolChain.installDir = "install-dir"
        toolChain.windowsSdkDir = "windows-sdk-dir"

        and:
        fileResolver.resolve("install-dir") >> file("vs")
        visualStudioLocator.locateVisualStudio(file("vs")) >> visualStudio
        visualStudio.found >> true

        and:
        fileResolver.resolve("windows-sdk-dir") >> file("win-sdk")
        visualStudioLocator.locateWindowsSdk(file("win-sdk")) >> windowsSdk
        windowsSdk.found >> true

        and:
        0 * _._

        then:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability);
        availability.available
    }

    def "resolves install directory"() {
        when:
        toolChain.installDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.installDir == file("one")
    }

    def "resolves windows sdk directory"() {
        when:
        toolChain.windowsSdkDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.windowsSdkDir == file("one")
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }
}
