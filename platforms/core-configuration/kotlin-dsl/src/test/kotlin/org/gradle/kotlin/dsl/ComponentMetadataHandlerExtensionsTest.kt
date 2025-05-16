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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler

import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock


class ComponentMetadataHandlerExtensionsTest {

    @Test
    fun all() {
        val componentMetadataHandler = mock<ComponentMetadataHandler> {
            on { all(any<Class<ComponentMetadataRule>>()) } doReturn mock<ComponentMetadataHandler>()
        }
        componentMetadataHandler.all<SomeRule>()
        inOrder(componentMetadataHandler) {
            verify(componentMetadataHandler).all(SomeRule::class.java)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun allWithParams() {
        val configAction = Config()
        val componentMetadataHandler = mock<ComponentMetadataHandler> {
            on { all(any<Class<ComponentMetadataRule>>(), any<Action<in ActionConfiguration>>()) } doReturn mock<ComponentMetadataHandler>()
        }
        componentMetadataHandler.all<SomeRule>(configAction)
        inOrder(componentMetadataHandler) {
            verify(componentMetadataHandler).all(SomeRule::class.java, configAction)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun withModule() {
        val componentMetadataHandler = mock<ComponentMetadataHandler> {
            on { withModule(eq("org:foo"), any<Class<ComponentMetadataRule>>()) } doReturn mock<ComponentMetadataHandler>()
        }
        componentMetadataHandler.withModule<SomeRule>("org:foo")
        inOrder(componentMetadataHandler) {
            verify(componentMetadataHandler).withModule("org:foo", SomeRule::class.java)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun withModuleWithParams() {
        val configAction = Config()
        val componentMetadataHandler = mock<ComponentMetadataHandler> {
            on { withModule(eq("org:foo"), any<Class<ComponentMetadataRule>>(), any<Action<in ActionConfiguration>>()) } doReturn mock<ComponentMetadataHandler>()
        }
        componentMetadataHandler.withModule<SomeRule>("org:foo", configAction)
        inOrder(componentMetadataHandler) {
            verify(componentMetadataHandler).withModule("org:foo", SomeRule::class.java, configAction)
            verifyNoMoreInteractions()
        }
    }
}


class SomeRule : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) = Unit
}


class Config : Action<ActionConfiguration> {
    override fun execute(config: ActionConfiguration) {
        config.params("p1", "p2")
    }
}
