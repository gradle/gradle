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
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.nativebinaries.ToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.toolchain.Gcc
import org.gradle.nativebinaries.toolchain.internal.gcc.GccToolChain
import org.gradle.util.TestUtil

class GccCompilerPluginTest extends ToolChainPluginTest {
    def project = TestUtil.createRootProject()

    @Override
    Class<? extends Plugin> getPluginClass() {
        GccCompilerPlugin
    }

    @Override
    Class<? extends ToolChain> getToolchainClass() {
        Gcc
    }

    @Override
    String getToolchainName() {
        "gcc"
    }

    def "makes a Gcc tool chain available"() {
        when:
        register()

        then:
        getToolchain() instanceof GccToolChain
    }

    @RequiresInstalledToolChain("gcc 4")
    def "registers default Gcc tool chain"() {
        when:
        addDefaultToolchain()

        then:
        getToolchain() instanceof GccToolChain
    }

    def "Gcc tool chain is extended"() {
        when:
        register()

        then:
        with (getToolchain()) {
            it instanceof ExtensionAware
            it.ext instanceof ExtraPropertiesExtension
        }
    }
}
