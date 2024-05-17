/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.internal.VersionNumber
import spock.lang.Specification

class VisualCppPlatformToolProviderTest extends Specification {
    def operatingSystem = Mock(OperatingSystemInternal)
    def visualCpp = Mock(VisualCpp)
    def windowsSdk = Mock(WindowsSdk)
    def ucrt = Mock(SystemLibraries)
    def visualStudioInstall = Mock(VisualStudioInstall)
    def toolProvider = new VisualCppPlatformToolProvider(Mock(BuildOperationExecutor), operatingSystem, [:], visualStudioInstall, visualCpp, windowsSdk, ucrt, Mock(ExecActionFactory), Mock(CompilerOutputFileNamingSchemeFactory), Mock(WorkerLeaseService))

    def "windows shared link file names end with lib"() {
        given:
        operatingSystem.internalOs >> OperatingSystem.WINDOWS

        expect:
        def actual = toolProvider.getSharedLibraryLinkFileName("sharedLibrary")
        actual == "sharedLibrary.lib"
    }

    def "system libraries contain union of VC++ and Windows SDK and UCRT"() {
        given:
        def dir1 = new File("1")
        def dir2 = new File("2")
        def dir3 = new File("3")
        visualCpp.includeDirs >> [dir1]
        windowsSdk.includeDirs >> [dir2]
        ucrt.includeDirs >> [dir3]

        expect:
        def libs = toolProvider.getSystemLibraries(ToolType.CPP_COMPILER)
        libs.includeDirs == [dir1, dir2, dir3]
    }

    def "returns compiler metadata"() {
        def vsVersion = VersionNumber.version(123)
        def vcVersion = VersionNumber.version(22)
        visualStudioInstall.version >> vsVersion
        visualCpp.implementationVersion >> vcVersion

        expect:
        def metadata = toolProvider.getCompilerMetadata(ToolType.CPP_COMPILER)
        metadata.visualStudioVersion == vsVersion
        metadata.version == vcVersion
        metadata.vendor == "Microsoft"
    }
}
