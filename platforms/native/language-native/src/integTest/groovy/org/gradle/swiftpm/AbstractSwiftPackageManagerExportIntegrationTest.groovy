/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.swift.SwiftPmRunner
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.test.fixtures.file.ExecOutput

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
abstract class AbstractSwiftPackageManagerExportIntegrationTest extends AbstractIntegrationSpec {
    def swiftc = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC_4)

    def setup() {
        settingsFile << """rootProject.name = 'test'
"""
    }

    ExecOutput swiftPmBuildSucceeds() {
        return SwiftPmRunner.create(swiftc)
            .withProjectDir(testDirectory)
            .withArguments("build")
            .build()
    }

    ExecOutput swiftPmBuildFails() {
        return SwiftPmRunner.create(swiftc)
                .withProjectDir(testDirectory)
                .withArguments("build")
                .buildAndFails()
    }
}
