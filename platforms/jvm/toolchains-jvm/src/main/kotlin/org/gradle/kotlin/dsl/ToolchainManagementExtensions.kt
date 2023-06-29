/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Incubating
import org.gradle.api.toolchain.management.ToolchainManagement
import org.gradle.jvm.toolchain.JvmToolchainManagement


/**
 * Provides statically defined accessors for configuring the "jvm" block in "toolchainManagement".
 * The "jvm-toolchain-management" plugin needs to be applied in order for these extensions to work.
 *
 * @since 7.6
 */
@Incubating
fun ToolchainManagement.jvm(block: JvmToolchainManagement.() -> Unit) {
    extensions.configure(JvmToolchainManagement::class.java, block)
}


/**
 * Provides statically defined accessors for getting the "jvm" block of "toolchainManagement".
 * The "jvm-toolchain-management" plugin needs to be applied in order for these extensions to work.
 *
 * @since 7.6
 */
@get:Incubating
val ToolchainManagement.jvm: JvmToolchainManagement
    get() = extensions.getByType(JvmToolchainManagement::class.java)
