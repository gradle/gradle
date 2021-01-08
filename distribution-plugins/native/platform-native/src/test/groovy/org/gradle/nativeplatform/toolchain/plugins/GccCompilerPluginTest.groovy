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
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.GccToolChain

class GccCompilerPluginTest extends NativeToolChainPluginTest {

    @Override
    Class<? extends Plugin> getPluginClass() {
        GccCompilerPlugin
    }

    @Override
    Class<? extends NativeToolChain> getToolchainClass() {
        Gcc
    }

    @Override
    String getToolchainName() {
        "gcc"
    }

    def "can apply plugin by id"() {
        given:
        project.apply plugin: 'gcc-compiler'

        expect:
        project.plugins.hasPlugin(pluginClass)
    }

    def "makes a Gcc tool chain available"() {
        when:
        register()

        then:
        toolchain instanceof GccToolChain
        toolchain.displayName == "Tool chain 'gcc' (GNU GCC)"
    }

    def "registers default Gcc tool chain"() {
        when:
        addDefaultToolchain()

        then:
        toolchain instanceof GccToolChain
    }
}
