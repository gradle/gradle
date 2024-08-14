/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.tasks

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.IncrementalCppStaleCompileOutputApp
import org.gradle.nativeplatform.fixtures.app.SourceElement

@RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
class ExtractSymbolsIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new IncrementalCppStaleCompileOutputApp()

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            plugins {
                id 'cpp-application'
            }

            task extractSymbolsDebug(type: ExtractSymbols) { extract ->
                project.application.binaries.get { !it.optimized }.configure {
                    def linkDebug = linkTask.get()
                    extract.toolChain = linkDebug.toolChain
                    extract.targetPlatform = linkDebug.targetPlatform
                    extract.binaryFile.set linkDebug.linkedFile
                }
                symbolFile.set file("build/symbols")
            }
        """
    }

    def "extracts symbols from binary"() {
        when:
        succeeds ":extractSymbolsDebug"

        then:
        executedAndNotSkipped":extractSymbolsDebug"
        fixture("build/symbols").assertHasDebugSymbolsFor(withoutHeaders(app.original))
    }

    def "extract is skipped when there are no changes"() {
        when:
        succeeds ":extractSymbolsDebug"

        then:
        executedAndNotSkipped":extractSymbolsDebug"

        when:
        succeeds ":extractSymbolsDebug"

        then:
        skipped":extractSymbolsDebug"
        fixture("build/symbols").assertHasDebugSymbolsFor(withoutHeaders(app.original))
    }

    def "extract is re-executed when changes are made"() {
        when:
        succeeds ":extractSymbolsDebug"

        then:
        executedAndNotSkipped":extractSymbolsDebug"

        when:
        app.applyChangesToProject(testDirectory)
        succeeds ":extractSymbolsDebug"

        then:
        executedAndNotSkipped":extractSymbolsDebug"
        fixture("build/symbols").assertHasDebugSymbolsFor(withoutHeaders(app.alternate))
    }

    NativeBinaryFixture fixture(String path) {
        return new NativeBinaryFixture(file(path), toolChain)
    }

    List<String> withoutHeaders(SourceElement sourceElement) {
        return sourceElement.sourceFileNames.findAll { !it.endsWith(".h") }
    }
}
