/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test

class KotlinDslToolchainManagementIntegrationTest : AbstractKotlinIntegrationTest() {

    /**
     * The `:toolchains-jvm` module injects hardcoded Kotlin extensions in the `org.gradle.kotlin.dsl` package.
     */
    @Test
    fun `can call settings toolchainManagement jvm in kotlin without any jvm toolchain plugin applied`() {

        // given:
        withSettings("""toolchainManagement { jvm {} }""")

        // when:
        val result = buildAndFail("help")

        // then:
        result.assertHasFailure("Extension of type 'JvmToolchainManagement' does not exist. Currently registered extension types: [ExtraPropertiesExtension]") {}
    }
}
