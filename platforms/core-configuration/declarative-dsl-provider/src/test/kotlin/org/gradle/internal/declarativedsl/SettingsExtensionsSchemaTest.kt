/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl

import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Action
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.plugins.ExtensionsSchema.ExtensionSchema
import org.gradle.api.provider.Property
import org.gradle.api.reflect.TypeOf
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.settings.settingsEvaluationSchema
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test


class SettingsExtensionsSchemaTest {

    @Test
    fun `settings extensions are imported in declarative dsl schema`() {
        val settingsMock: SettingsInternal = run {
            val extensionsMock = run {
                val myExtensionMock = mock<MyExtension>()
                val extensionsSchemaMock = mock<ExtensionsSchema> {
                    on { elements }.thenReturn(listOf<ExtensionSchema>(object : ExtensionSchema {
                        override fun getName(): String = "myExtension"
                        override fun getPublicType(): TypeOf<*> = TypeOf.typeOf(MyExtension::class.java)
                    }))
                }
                mock<ExtensionContainer> {
                    on { extensionsSchema }.thenReturn(extensionsSchemaMock)
                    on { getByName("myExtension") }.thenReturn(myExtensionMock)
                }
            }
            mock<SettingsInternal> {
                on { extensions }.thenReturn(extensionsMock)
            }
        }

        val schema = settingsEvaluationSchema(settingsMock)

        val schemaType = schema.analysisSchema.dataClassTypesByFqName.values
            .filterIsInstance<DataClass>()
            .find { it.name.simpleName == MyExtension::class.simpleName }
        assertNotNull(schemaType)
        assertTrue(schemaType!!.properties.any { it.name == "id" })

        assertTrue(schema.analysisSchema.dataClassTypesByFqName.keys.any { it.simpleName == MyNestedType::class.simpleName })
    }

    @Restricted
    internal
    abstract class MyExtension {
        @get:Restricted
        abstract val id: Property<String>

        @Configuring
        abstract fun nested(action: Action<in MyNestedType>)
    }

    internal
    class MyNestedType
}
