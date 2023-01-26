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
import org.gradle.api.NamedDomainObjectCollectionSchema
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.extensibility.DefaultExtensionsSchema
import org.gradle.kotlin.dsl.accessors.ProjectSchemaEntry
import org.gradle.kotlin.dsl.provider.plugins.targetSchemaFor
import org.gradle.kotlin.dsl.provider.plugins.typeOf
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class DefaultProjectSchemaProviderTest {

    private
    interface Extension : ExtensionAware

    private
    fun <T> extensionSchema(name: String, publicType: TypeOf<T>): ExtensionsSchema.ExtensionSchema = mock {
        on { getName() } doReturn name
        on { getPublicType() } doReturn publicType
    }

    @Test
    fun `ignore containers with generic types`() {
        val specificContainerSchema = mock<NamedDomainObjectCollectionSchema.NamedDomainObjectSchema> {
            on { name } doReturn "customSourceSet"
            on { publicType } doReturn typeOf<SourceSet>()
        }

        val specificContainerCollectionSchema = mock<NamedDomainObjectCollectionSchema> {
            on { elements } doReturn listOf(specificContainerSchema)
        }

        val genericContainer = mock<NamedDomainObjectContainer<Any>> {}
        val specificContainer = mock<SourceSetContainer> {
            on { collectionSchema } doReturn specificContainerCollectionSchema
        }

        val extensionWithContainer = mock<ExtensionWithContainers> {
            on { getGenericContainer() } doReturn genericContainer
            on { getSpecificContainer() } doReturn specificContainer
        }

        val extensionSchemas = DefaultExtensionsSchema.create(
            listOf(
                extensionSchema(
                    "extensionWithContainers",
                    typeOf<ExtensionWithContainers>()
                )
            )
        )

        val extensionContainer = mock<ExtensionContainerInternal> {
            on { extensionsSchema } doReturn extensionSchemas
            on { getByName("extensionWithContainers") } doReturn extensionWithContainer
        }

        val extension = mock<Extension> {
            on { extensions } doReturn extensionContainer
        }

        assertThat(
            targetSchemaFor(
                extension,
                typeOf<Extension>()
            ).containerElements,
            equalTo(
                listOf(
                    ProjectSchemaEntry(
                        typeOf<SourceSetContainer>(),
                        "customSourceSet",
                        typeOf<SourceSet>()
                    )
                )
            ))
    }

    private
    interface ExtensionWithContainers {
        fun getGenericContainer(): NamedDomainObjectContainer<Any>

        fun getSpecificContainer(): SourceSetContainer
    }

    @Test
    fun `chooses first public interface in type hierarchy`() {
        val extensionSchemas = DefaultExtensionsSchema.create(
            listOf(
                extensionSchema(
                    "kotlinOptions",
                    typeOf<KotlinJvmOptionsImpl>()
                )
            )
        )

        val extensionContainer = mock<ExtensionContainerInternal> {
            on { extensionsSchema } doReturn extensionSchemas
            on { getByName("kotlinOptions") } doReturn KotlinJvmOptionsImpl()
        }

        val extension = mock<Extension> {
            on { extensions } doReturn extensionContainer
        }

        assertThat(
            targetSchemaFor(
                extension,
                typeOf<Extension>()
            ).extensions,
            equalTo(
                listOf(
                    ProjectSchemaEntry(
                        typeOf<Extension>(),
                        "kotlinOptions",
                        typeOf<KotlinJvmOptions>()
                    )
                )
            )
        )
    }

    private
    class KotlinJvmOptionsImpl : KotlinJvmOptionsBase()

    private
    open class KotlinJvmOptionsBase : KotlinJvmOptions

    interface KotlinJvmOptions
}
