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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.text.TreeFormatter
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import spock.lang.Specification

class VisualCppToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final ExecActionFactory execActionFactory = Mock(ExecActionFactory)
    final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory = Mock(CompilerOutputFileNamingSchemeFactory)
    final BuildOperationExecutor buildOperationExecutor = Stub(BuildOperationExecutor)
    final VisualStudioLocator.SearchResult visualStudioLookup = Stub(VisualStudioLocator.SearchResult)
    final WindowsSdkLocator.SearchResult windowsSdkLookup = Stub(WindowsSdkLocator.SearchResult)
	final UcrtLocator.SearchResult ucrtLookup = Stub(UcrtLocator.SearchResult)
    final WorkerLeaseService workerLeaseService = Stub(WorkerLeaseService)
    final Instantiator instantiator = DirectInstantiator.INSTANCE
    VisualCppToolChain toolChain

    final VisualStudioLocator visualStudioLocator = Stub(VisualStudioLocator) {
        locateDefaultVisualStudioInstall(_) >> visualStudioLookup
    }
    final WindowsSdkLocator windowsSdkLocator = Stub(WindowsSdkLocator) {
        locateWindowsSdks(_) >> windowsSdkLookup
    }
    final UcrtLocator ucrtLocator = Stub(UcrtLocator) {
        locateUcrts(_) >> ucrtLookup
    }
	final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
    }

    def setup() {
        toolChain = new VisualCppToolChain("visualCpp", buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, visualStudioLocator, windowsSdkLocator, ucrtLocator, instantiator, workerLeaseService)
    }

    def "installs an unavailable tool chain when not windows"() {
        given:
        def operatingSystem = Stub(OperatingSystem)
        operatingSystem.isWindows() >> false
		ucrtLookup.available >> false
        def toolChain = new VisualCppToolChain("visualCpp", buildOperationExecutor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, visualStudioLocator, windowsSdkLocator, ucrtLocator, instantiator, workerLeaseService)

        when:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == 'Visual Studio is not available on this operating system.'
    }

    def "is not available when visual studio installation cannot be located"() {
        when:
        visualStudioLookup.available >> false
        visualStudioLookup.explain(_) >> { TreeVisitor<String> visitor -> visitor.node("vs install not found anywhere") }
        windowsSdkLookup.available >> false
		ucrtLookup.available >> false

        and:
        def result = toolChain.select(Stub(NativePlatformInternal))

        then:
        !result.available
        getMessage(result) == "vs install not found anywhere"
    }

    def "is not available when windows SDK cannot be located"() {
        when:
        visualStudioLookup.available >> true

        windowsSdkLookup.available >> false
        windowsSdkLookup.explain(_) >> { TreeVisitor<String> visitor -> visitor.node("sdk not found anywhere") }
		ucrtLookup.available >> false

        and:
        def result = toolChain.select(Stub(NativePlatformInternal))

        then:
        !result.available
        getMessage(result) == "sdk not found anywhere"
    }

    def "is not available when visual studio installation and windows SDK can be located and visual studio install does not support target platform"() {
        when:
        def visualStudio = Stub(VisualStudioInstall)
        def visualCpp = Stub(VisualCppInstall)
        def platform = Stub(NativePlatformInternal) { getName() >> 'platform' }
        visualStudioLookup.available >> true
        windowsSdkLookup.available >> true
		ucrtLookup.available >> false
        visualStudioLookup.visualStudio >> visualStudio
        visualStudioLookup.visualStudio >> Stub(VisualStudioInstall)
        visualStudio.visualCpp >> visualCpp
        visualCpp.isSupportedPlatform(platform) >> false

        and:
        def result = toolChain.select(platform)

        then:
        !result.available
        getMessage(result) == "Don't know how to build for platform 'platform'."
    }

    def "is available when visual studio installation and windows SDK can be located and visual studio install supports target platform"() {
        when:
        def visualStudio = Stub(VisualStudioInstall)
        def visualCpp = Stub(VisualCppInstall)
        def platform = Stub(NativePlatformInternal)
        visualStudioLookup.available >> true
        windowsSdkLookup.available >> true
		ucrtLookup.available >> false
        visualStudioLookup.visualStudio >> visualStudio
        visualStudioLookup.visualStudio >> Stub(VisualStudioInstall)
        visualStudio.visualCpp >> visualCpp
        visualCpp.isSupportedPlatform(platform) >> true

        and:
        def platformToolChain = toolChain.select(platform)

        then:
        platformToolChain.available
    }

    def "uses provided installDir and windowsSdkDir for location"() {
        when:
        toolChain.installDir = "install-dir"
        toolChain.windowsSdkDir = "windows-sdk-dir"

        and:
        fileResolver.resolve("install-dir") >> file("vs")
        visualStudioLocator.locateDefaultVisualStudioInstall(file("vs")) >> visualStudioLookup
        visualStudioLookup.available >> true

        and:
        fileResolver.resolve("windows-sdk-dir") >> file("win-sdk")
        windowsSdkLocator.locateWindowsSdks(file("win-sdk")) >> windowsSdkLookup
        windowsSdkLookup.available >> true
		ucrtLookup.available >> false

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

    def "provided action can configure platform tool chain"() {
        given:
        def platform = Stub(NativePlatformInternal)
        def visualStudio = Stub(VisualStudioInstall)
        def visualCpp = Stub(VisualCppInstall)
        visualStudioLookup.available >> true
        windowsSdkLookup.available >> true
		ucrtLookup.available >> false
        visualStudioLookup.visualStudio >> visualStudio
        visualStudioLookup.visualStudio >> Stub(VisualStudioInstall)
        visualStudio.visualCpp >> visualCpp
        visualCpp.isSupportedPlatform(platform) >> true

        def action = Mock(Action)
        toolChain.eachPlatform(action)

        when:
        toolChain.select(platform)

        then:
        1 * action.execute(_) >> { VisualCppPlatformToolChain platformToolChain ->
            assert platformToolChain.platform == platform
            assert platformToolChain.assembler
            assert platformToolChain.cCompiler
            assert platformToolChain.cppCompiler
            assert platformToolChain.rcCompiler
            assert platformToolChain.linker
            assert platformToolChain.staticLibArchiver
        }
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }

    def getMessage(ToolSearchResult result) {
        def formatter = new TreeFormatter()
        result.explain(formatter)
        return formatter.toString()
    }
}
