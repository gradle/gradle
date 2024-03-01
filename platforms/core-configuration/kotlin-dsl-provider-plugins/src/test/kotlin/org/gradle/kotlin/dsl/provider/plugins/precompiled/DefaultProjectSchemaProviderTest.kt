/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins.precompiled

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.extensibility.DefaultExtensionsSchema
import org.gradle.kotlin.dsl.accessors.ProjectSchemaEntry
import org.gradle.kotlin.dsl.provider.plugins.targetSchemaFor
import org.gradle.kotlin.dsl.provider.plugins.typeOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class DefaultProjectSchemaProviderTest {

    @Test
    fun `chooses first public interface in type hierarchy`() {

        val androidExtensionsSchema = DefaultExtensionsSchema.create(
            listOf(
                extensionSchema(
                    "kotlinOptions",
                    typeOf<KotlinJvmOptionsImpl>()
                )
            )
        )

        val androidExtensions = mock<ExtensionContainerInternal> {
            on { extensionsSchema } doReturn androidExtensionsSchema
            on { getByName("kotlinOptions") } doReturn KotlinJvmOptionsImpl()
        }

        val androidExtension = mock<AndroidExtension> {
            on { extensions } doReturn androidExtensions
        }

        assertThat(
            targetSchemaFor(
                androidExtension,
                typeOf<AndroidExtension>()
            ).extensions,
            equalTo(
                listOf(
                    ProjectSchemaEntry(
                        typeOf<AndroidExtension>(),
                        "kotlinOptions",
                        typeOf<KotlinJvmOptions>()
                    )
                )
            )
        )
    }

    interface AndroidExtension : ExtensionAware

    internal
    class KotlinJvmOptionsImpl : KotlinJvmOptionsBase()

    internal
    open class KotlinJvmOptionsBase : KotlinJvmOptions

    interface KotlinJvmOptions

    private
    fun <T> extensionSchema(name: String, publicType: TypeOf<T>): ExtensionsSchema.ExtensionSchema = mock {
        on { getName() } doReturn name
        on { getPublicType() } doReturn publicType
    }
}
