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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test


class KotlinDslTemplatesDeprecationsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Suppress("DEPRECATION")
    fun `InitScriptApi deprecation`() {
        withBuildScript("""
            import org.gradle.api.internal.file.FileOperations
            import org.gradle.api.internal.ProcessOperations
            import org.gradle.kotlin.dsl.support.serviceOf

            class DeprecationTrigger(gradle: Gradle) : ${org.gradle.kotlin.dsl.InitScriptApi::class.qualifiedName}(gradle) {
                override val fileOperations: FileOperations = gradle.serviceOf()
                override val processOperations: ProcessOperations = gradle.serviceOf()
            }

            DeprecationTrigger(gradle)
        """)
        executer.expectDeprecationWarning("The org.gradle.kotlin.dsl.InitScriptApi type has been deprecated. This is scheduled to be removed in Gradle 9.0.")
        build("help")
    }


    @Test
    @Suppress("DEPRECATION")
    fun `SettingsScriptApi deprecation`() {
        withSettings("""
            import org.gradle.api.internal.file.FileOperations
            import org.gradle.api.internal.ProcessOperations
            import org.gradle.kotlin.dsl.support.serviceOf

            class DeprecationTrigger(settings: Settings) : ${org.gradle.kotlin.dsl.SettingsScriptApi::class.qualifiedName}(settings) {
                override val fileOperations: FileOperations = settings.serviceOf()
                override val processOperations: ProcessOperations = settings.serviceOf()
            }

            DeprecationTrigger(settings)
        """)
        executer.expectDeprecationWarning("The org.gradle.kotlin.dsl.SettingsScriptApi type has been deprecated. This is scheduled to be removed in Gradle 9.0.")
        build("help")
    }
}
