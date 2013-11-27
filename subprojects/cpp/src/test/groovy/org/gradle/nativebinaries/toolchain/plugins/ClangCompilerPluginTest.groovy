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

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.nativebinaries.toolchain.Clang
import org.gradle.nativebinaries.toolchain.internal.clang.ClangToolChain
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TestUtil
import spock.lang.Specification

class ClangCompilerPluginTest extends Specification {
    def project = TestUtil.createRootProject()

    def setup() {
        project.plugins.apply(ClangCompilerPlugin)
    }

    def "makes a Clang tool chain available"() {
        when:
        project.toolChains.create("clang", Clang)

        then:
        project.toolChains.clang instanceof ClangToolChain
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "registers default Clang tool chain"() {
        when:
        project.toolChains.addDefaultToolChain()

        then:
        project.toolChains.clang instanceof ClangToolChain
    }

    def "Clang tool chain is extended"() {
        when:
        project.toolChains.create("clang", Clang)

        then:
        with(project.toolChains.clang) {
            it instanceof ExtensionAware
            it.ext instanceof ExtraPropertiesExtension
        }
    }
}
