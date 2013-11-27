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

package org.gradle.nativebinaries.toolchain.plugins

import org.gradle.api.Plugin
import org.gradle.nativebinaries.ToolChain
import org.gradle.nativebinaries.ToolChainRegistry
import org.gradle.nativebinaries.internal.ToolChainInternal
import org.gradle.util.TestUtil
import spock.lang.Specification

abstract class ToolChainPluginTest extends Specification {

    def project = TestUtil.createRootProject()

    def setup() {
        project.plugins.apply(getPluginClass())
    }

    abstract Class<? extends Plugin> getPluginClass()

    abstract Class<? extends ToolChain> getToolchainClass()

    String getToolchainName() {
        "toolchain"
    }

    ToolChainInternal getToolchain() {
        project.modelRegistry.get("toolChains", ToolChainRegistry).getByName(getToolchainName()) as ToolChainInternal
    }

    void register() {
        project.model {
            toolChains {
                create(getToolchainName(), getToolchainClass())
            }
        }
    }

    void addDefaultToolchain() {
        project.model { toolChains { addDefaultToolChains() } }
    }
}
