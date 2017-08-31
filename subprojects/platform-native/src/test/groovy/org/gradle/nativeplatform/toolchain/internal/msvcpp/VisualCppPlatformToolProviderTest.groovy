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
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.process.internal.ExecActionFactory
import spock.lang.Specification

class VisualCppPlatformToolProviderTest extends Specification {
    def "windows shared link file names end with lib"() {
        given:
        def operatingSystem = Mock(OperatingSystemInternal)
        VisualCppPlatformToolProvider toolProvider = new VisualCppPlatformToolProvider(Mock(BuildOperationExecutor), operatingSystem, [:], Mock(VisualCppInstall), Mock(WindowsSdk), Mock(Ucrt), Mock(NativePlatformInternal), Mock(ExecActionFactory), Mock(CompilerOutputFileNamingSchemeFactory), Mock(WorkerLeaseService))

        when:
        operatingSystem.getInternalOs() >> OperatingSystem.WINDOWS
        and:
        def actual = toolProvider.getSharedLibraryLinkFileName("sharedLibrary")

        then:
        actual == "sharedLibrary.lib"
    }
}
