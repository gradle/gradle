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
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.clang.ClangToolChain

class ClangCompilerPluginTest extends NativeToolChainPluginTest {

    @Override
    Class<? extends Plugin> getPluginClass() {
        ClangCompilerPlugin
    }

    @Override
    Class<? extends NativeToolChain> getToolchainClass() {
        Clang
    }

    @Override
    String getToolchainName() {
        "clang"
    }

    def "can apply plugin by id"() {
        given:
        project.apply plugin: 'clang-compiler'

        expect:
        project.plugins.hasPlugin(pluginClass)
    }

    def "makes a Clang tool chain available"() {
        when:
        register()

        then:
        toolchain instanceof ClangToolChain
        toolchain.displayName == "Tool chain 'clang' (Clang)"
    }

    def "registers default Clang tool chain"() {
        when:
        addDefaultToolchain()

        then:
        toolchain instanceof ClangToolChain
    }
}
