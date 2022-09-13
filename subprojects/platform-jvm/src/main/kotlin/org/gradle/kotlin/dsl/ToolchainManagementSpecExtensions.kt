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

package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.toolchain.management.ToolchainManagementSpec
import org.gradle.jvm.toolchain.JdksBlockForToolchainManagement


/**
 * Provides statically defined accessors for configuring the "jdks" block in "toolchainManagement".
 * This block is added to ToolchainManagementSpec dynamically, by the "jvm-toolchains" plugin
 * and normally the dynamic extension should generate accessors, but this doesn't work
 * at the level of Settings, hence the need for the static accessors.
 *
 * @since 7.6
 */
@Incubating
fun ToolchainManagementSpec.jdks(block: JdksBlockForToolchainManagement.() -> Unit) {
    extensions.configure(JdksBlockForToolchainManagement::class.java, block)
}


/**
 * Provides statically defined accessors for getting the "jdks" block of "toolchainManagement".
 * This block is added to ToolchainManagementSpec dynamically, by the "jvm-toolchains" plugin
 * and normally the dynamic extension should generate accessors, but this doesn't work
 * at the level of Settings, hence the need for the static accessors.
 *
 * @since 7.6
 */
@get:Incubating
val ToolchainManagementSpec.jdks: JdksBlockForToolchainManagement
    get() = extensions.getByType(JdksBlockForToolchainManagement::class.java)
