/*
 * Copyright 2018 the original author or authors.
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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.junit.Test


class ScriptHandlerScopeTest {
    @Test
    fun `can use a provider for the dependency declaration`() {
        val dependency = mock<ExternalModuleDependency>()
        val dependencies = newDependencyHandlerMock()
        val notation = mock<Provider<MinimalExternalModuleDependency>>()
        val buildscript = mock<ScriptHandler> {
            on { getDependencies() } doReturn dependencies
        }

        ScriptHandlerScope(buildscript).apply {
            dependencies {
                classpath(notation)
                classpath(notation) { exclude(module = "com.google.guava") }
            }
        }

        verify(dependencies).add(eq("classpath"), eq(notation))
        verify(dependencies).addProvider(eq("classpath"), eq(notation), any<Action<ExternalModuleDependency>>())
        // Because it's a Provider, this isn't called until evaluation
        verify(dependency, never()).exclude(mapOf("module" to "com.google.guava"))
    }

    @Test
    fun `can use a provider convertible for the dependency declaration`() {
        val dependency = mock<ExternalModuleDependency>()
        val dependencies = newDependencyHandlerMock()
        val notation = mock<ProviderConvertible<MinimalExternalModuleDependency>>()
        val buildscript = mock<ScriptHandler> {
            on { getDependencies() } doReturn dependencies
        }

        ScriptHandlerScope(buildscript).apply {
            dependencies {
                classpath(notation)
                classpath(notation) { exclude(module = "com.google.guava") }
            }
        }

        verify(dependencies).add(eq("classpath"), eq(notation))
        verify(dependencies).addProviderConvertible(eq("classpath"), eq(notation), any<Action<ExternalModuleDependency>>())
        // Because it's a Provider, this isn't called until evaluation
        verify(dependency, never()).exclude(mapOf("module" to "com.google.guava"))
    }

    @Test
    fun `can exclude modules from classpath dependency`() {

        val dependency = mock<ExternalModuleDependency>()
        val dependencies = newDependencyHandlerMock {
            on { create("...") } doReturn dependency
        }
        val buildscript = mock<ScriptHandler> {
            on { getDependencies() } doReturn dependencies
        }

        ScriptHandlerScope(buildscript).apply {
            dependencies {
                classpath("...") { exclude(module = "com.google.guava") }
            }
        }

        verify(dependency).exclude(mapOf("module" to "com.google.guava"))
    }

    @Test
    fun `can declare classpath dependency constraints`() {

        val constraint = mock<DependencyConstraint>()
        val constraintHandler = mock<DependencyConstraintHandler> {
            on { add(any(), any()) } doReturn constraint
            on { add(any(), any(), any()) } doReturn constraint
        }
        val dependencies = newDependencyHandlerMock {
            on { constraints } doReturn constraintHandler
            on { constraints(any()) } doAnswer {
                (it.getArgument(0) as Action<DependencyConstraintHandler>).execute(constraintHandler)
            }
        }
        val buildscript = mock<ScriptHandler> {
            on { getDependencies() } doReturn dependencies
        }

        ScriptHandlerScope(buildscript).apply {
            dependencies.constraints.classpath("direct:accessor")
            dependencies {
                constraints.classpath("direct-in-dep-handler-scope:accessor")
            }
            dependencies {
                constraints {
                    classpath("in-block:accessor")
                    classpath("in-block:accessor-with-action") {
                        because("just because")
                    }
                }
            }
        }

        inOrder(constraintHandler) {
            verify(constraintHandler).add(eq("classpath"), eq("direct:accessor"))
            verify(constraintHandler).add(eq("classpath"), eq("direct-in-dep-handler-scope:accessor"))
            verify(constraintHandler).add(eq("classpath"), eq("in-block:accessor"))
            verify(constraintHandler).add(eq("classpath"), eq("in-block:accessor-with-action"), any())
            verifyNoMoreInteractions()
        }
    }
}
