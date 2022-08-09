/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins


import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class JvmToolchainsPluginTest extends AbstractProjectBuilderSpec {

    def setup() {
        project.pluginManager.apply(JvmToolchainsPlugin)
    }

    def "available under jvm-toolchains id"() {
        expect:
        project.pluginManager.hasPlugin("jvm-toolchains")
    }

    def "registers javaToolchains extension"() {
        expect:
        project.extensions.getByType(JavaToolchainService) == project.extensions.getByName("javaToolchains")
    }

    def "toolchain service dependencies are satisfied"() {
        expect:
        project.extensions.getByType(JavaToolchainService).launcherFor(new CurrentJvmToolchainSpec(project.objects)).get().executablePath.asFile.isFile()
    }
}
