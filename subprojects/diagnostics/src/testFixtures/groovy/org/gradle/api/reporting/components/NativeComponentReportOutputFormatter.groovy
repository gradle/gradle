/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.components

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture

class NativeComponentReportOutputFormatter extends ComponentReportOutputFormatter {
    final AvailableToolChains.InstalledToolChain toolChain

    NativeComponentReportOutputFormatter() {
        this.toolChain = AvailableToolChains.getDefaultToolChain()
    }

    NativeComponentReportOutputFormatter(AvailableToolChains.InstalledToolChain toolChain) {
        this.toolChain = toolChain
    }

    @Override
    String transform(String original) {
        return super.transform(
            original
            .replace("Tool chain 'clang' (Clang)", toolChain.instanceDisplayName)
            .replace("platform 'current'", "platform '${NativePlatformsTestFixture.defaultPlatformName}'")
            .replaceAll('(?m)(build/libs/.+/)lib(\\w+).dylib$') { it[1] + OperatingSystem.current().getSharedLibraryName(it[2]) }
            .replaceAll('(?m)(build/libs/.+/)lib(\\w+).a$') { it[1] + OperatingSystem.current().getStaticLibraryName(it[2]) }
            .replaceAll('(?m)(build/exe/.+/)(\\w+)$') { it[1] + OperatingSystem.current().getExecutableName(it[2]) }
        )
    }
}
