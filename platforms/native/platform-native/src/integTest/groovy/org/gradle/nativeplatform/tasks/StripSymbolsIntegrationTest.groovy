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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativeBinaryFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.IncrementalCppStaleCompileOutputApp
import org.gradle.nativeplatform.fixtures.app.SourceElement

@RequiresInstalledToolChain(ToolChainRequirement.GCC_COMPATIBLE)
class StripSymbolsIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def app = new IncrementalCppStaleCompileOutputApp()

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        app.writeToProject(testDirectory)
        buildFile << """
            plugins {
                id 'cpp-application'
            }

            task stripSymbolsDebug(type: StripSymbols) { strip ->
                project.application.binaries.get { !it.optimized }.configure {
                    def linkDebug = linkTask.get()
                    strip.toolChain = linkDebug.toolChain
                    strip.targetPlatform = linkDebug.targetPlatform
                    strip.binaryFile.set linkDebug.linkedFile
                }
                outputFile.set file("build/stripped")
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "strips symbols from binary"() {
        when:
        succeeds ":stripSymbolsDebug"

        then:
        executedAndNotSkipped":stripSymbolsDebug"
        executable("build/exe/main/debug/app").assertHasDebugSymbolsFor(withoutHeaders(app.original))
        binary("build/stripped").assertDoesNotHaveDebugSymbolsFor(withoutHeaders(app.original))
    }

    @ToBeFixedForConfigurationCache
    def "strip is skipped when there are no changes"() {
        when:
        succeeds ":stripSymbolsDebug"

        then:
        executedAndNotSkipped":stripSymbolsDebug"

        when:
        succeeds ":stripSymbolsDebug"

        then:
        skipped":stripSymbolsDebug"
        binary("build/stripped").assertDoesNotHaveDebugSymbolsFor(withoutHeaders(app.original))
    }

    @ToBeFixedForConfigurationCache
    def "strip is re-executed when changes are made"() {
        when:
        succeeds ":stripSymbolsDebug"

        then:
        executedAndNotSkipped":stripSymbolsDebug"

        when:
        app.applyChangesToProject(testDirectory)
        succeeds ":stripSymbolsDebug"

        then:
        executedAndNotSkipped":stripSymbolsDebug"
        binary("build/stripped").assertDoesNotHaveDebugSymbolsFor(withoutHeaders(app.alternate))
    }

    NativeBinaryFixture binary(String path) {
        return new NativeBinaryFixture(file(path), toolChain)
    }

    List<String> withoutHeaders(SourceElement sourceElement) {
        return sourceElement.sourceFileNames.findAll { !it.endsWith(".h") }
    }
}
