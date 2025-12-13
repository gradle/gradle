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

package org.gradle.nativeplatform.toolchain.plugins

import org.gradle.api.Plugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

abstract class NativeToolChainPluginTest extends AbstractProjectBuilderSpec {
    NativeToolChainRegistryInternal registry
    def setup() {
        project.pluginManager.apply(getPluginClass())
        registry = project.toolChains
    }

    abstract Class<? extends Plugin> getPluginClass()

    abstract Class<? extends NativeToolChain> getToolchainClass()

    String getToolchainName() {
        "toolchain"
    }

    NativeToolChainInternal getToolchain() {
        registry.getByName(getToolchainName()) as NativeToolChainInternal
    }

    void register() {
        registry.create(getToolchainName(), getToolchainClass())
    }

    void addDefaultToolchain() {
        registry.addDefaultToolChains()
    }

    def "tool chain is extended"() {
        when:
        register()

        then:
        with(toolchain) {
            it instanceof ExtensionAware
            it.ext instanceof ExtraPropertiesExtension
        }
    }
}
