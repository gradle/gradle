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

package org.gradle.ide.xcode.fixtures

import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.hamcrest.Matchers

import static org.junit.Assume.assumeThat
import static org.junit.Assume.assumeTrue

trait SwiftToolChainTestingSpec {
    def toolChain = null

    void requireSwiftToolChain() {
        toolChain = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFT)
        assumeTrue(toolChain != null && toolChain.isAvailable())

        File initScript = file("init.gradle") << """
            allprojects { p ->
                apply plugin: ${toolChain.pluginClass}

                model {
                      toolChains {
                        ${toolChain.buildScriptConfig}
                      }
                }
            }
        """
        executer.beforeExecute({
            usingInitScript(initScript)
        })
    }

    void assumeSwiftCompilerVersion(int major) {
        assert toolChain != null, "You need to specify Swift tool chain requirement with 'requireSwiftToolChain()'"
        assumeThat(toolChain.version.major, Matchers.equalTo(major))
    }
}
