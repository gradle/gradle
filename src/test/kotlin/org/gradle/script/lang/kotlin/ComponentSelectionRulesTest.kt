package org.gradle.script.lang.kotlin

import com.nhaarman.mockito_kotlin.KStubbing
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolutionStrategy

import org.junit.Test

class ComponentSelectionRulesTest {

    @Test
    fun `Action method overloads are correctly selected`() {

        val componentSelection = mock<ComponentSelection>()
        val componentSelectionRules = mock<ComponentSelectionRules> {
            acceptConfigurationActionFor(componentSelection, ComponentSelectionRules::all)
        }
        val resolutionStrategy = mock<ResolutionStrategy> {
            acceptConfigurationActionFor(componentSelectionRules, ResolutionStrategy::componentSelection)
        }
        val conf = mock<Configuration> {
            acceptConfigurationActionFor(resolutionStrategy, Configuration::resolutionStrategy)
        }
        val configurations = mock<ConfigurationContainer> {
            on { create("conf") } doReturn conf
        }

        configurations {
            "conf" {
                resolutionStrategy {
                    it.componentSelection {
                        it.all { selection ->
                            selection.reject("all")
                        }
                    }
                }
            }
        }
        verify(componentSelection).reject("all")
    }

    private fun <T, E> KStubbing<T>.acceptConfigurationActionFor(element: E, method: T.(Action<E>) -> T) {
        on { method(any()) }.thenAnswer { invocation ->
            invocation.getArgument<Action<E>>(0).execute(element)
            invocation.mock
        }
    }
}
