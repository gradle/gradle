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

package org.gradle.language.swift

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.nativeplatform.fixtures.NativeInstallationFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.SharedLibraryFixture
import org.gradle.nativeplatform.fixtures.ToolChainRequirement

import static org.junit.Assume.assumeTrue

@RequiresInstalledToolChain(ToolChainRequirement.SWIFT)
class AbstractSwiftMixedLanguageIntegrationTest extends AbstractIntegrationSpec {
    def swiftToolChain = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFT) as AvailableToolChains.InstalledToolChain
    def cppToolChain = AvailableToolChains.getToolChain(ToolChainRequirement.CLANG) as AvailableToolChains.InstalledToolChain

    def setup() {
        assumeTrue(swiftToolChain != null && swiftToolChain.isAvailable())
        assumeTrue(cppToolChain != null && cppToolChain.isAvailable())

        File initScript = file("init.gradle") << """
        allprojects { p ->        
            p.plugins.withType(${swiftToolChain.pluginClass}) {
                model {
                    toolChains {
                        ${swiftToolChain.buildScriptConfig}
                    }
                }
            }
            p.plugins.withType(${cppToolChain.pluginClass}) {
                model {
                    toolChains {
                        ${cppToolChain.buildScriptConfig}
                    }
                }
            }
        }
        """
        executer.beforeExecute({
            usingInitScript(initScript)
        })
    }

    NativeInstallationFixture installation(Object installDir, OperatingSystem os = OperatingSystem.current()) {
        return new NativeInstallationFixture(file(installDir), os)
    }

    ExecutableFixture executable(Object path) {
        return swiftToolChain.executable(file(path))
    }

    SharedLibraryFixture swiftLibrary(Object path) {
        return swiftToolChain.sharedLibrary(file(path))
    }

    SharedLibraryFixture cppLibrary(Object path) {
        return cppToolChain.sharedLibrary(file(path))
    }
}
