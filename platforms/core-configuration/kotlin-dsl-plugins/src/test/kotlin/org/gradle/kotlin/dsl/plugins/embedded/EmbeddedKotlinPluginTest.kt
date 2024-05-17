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

package org.gradle.kotlin.dsl.plugins.embedded

import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.*
import org.junit.Test


class EmbeddedKotlinPluginTest {
    @Test
    fun `emit a warning if the kotlin plugin version is not the same as embedded`() {

        val logger = mock<Logger>()
        val template = """
            WARNING: Unsupported Kotlin plugin version.
            The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `{}` that might work differently than in the requested version `{}`.
        """.trimIndent()


        logger.warnOnDifferentKotlinVersion(embeddedKotlinVersion)

        inOrder(logger) {
            verifyNoMoreInteractions()
        }


        logger.warnOnDifferentKotlinVersion("1.3")

        inOrder(logger) {
            verify(logger).warn(template, embeddedKotlinVersion, "1.3")
            verifyNoMoreInteractions()
        }


        logger.warnOnDifferentKotlinVersion(null)

        inOrder(logger) {
            verify(logger).warn(template, embeddedKotlinVersion, null)
            verifyNoMoreInteractions()
        }
    }
}
