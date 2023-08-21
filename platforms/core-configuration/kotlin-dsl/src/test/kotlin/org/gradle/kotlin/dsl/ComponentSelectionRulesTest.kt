package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolutionStrategy

import org.junit.Test

import org.mockito.invocation.InvocationOnMock


class ComponentSelectionRulesTest {

    @Test
    fun `Action method overloads are correctly selected`() {

        val componentSelection = mock<ComponentSelection>()
        val componentSelectionRules = mock<ComponentSelectionRules> {
            on { all(any<Action<ComponentSelection>>()) }.thenAnswer {
                it.executeActionOn(componentSelection)
            }
        }
        val resolutionStrategy = mock<ResolutionStrategy> {
            on { componentSelection(any<Action<ComponentSelectionRules>>()) }.thenAnswer {
                it.executeActionOn(componentSelectionRules)
            }
        }
        val conf = mock<Configuration> {
            on { resolutionStrategy(any<Action<ResolutionStrategy>>()) }.thenAnswer {
                it.executeActionOn(resolutionStrategy)
            }
        }
        val configurations = mock<ConfigurationContainer> {
            on { create(eq("conf"), any<Action<Configuration>>()) }.thenAnswer {
                it.getArgument<Action<Configuration>>(1).execute(conf)
                conf
            }
        }

        configurations {
            create("conf") {
                resolutionStrategy {
                    componentSelection {
                        all {
                            reject("all")
                        }
                    }
                }
            }
        }
        verify(componentSelection).reject("all")
    }

    private
    fun <E : Any> InvocationOnMock.executeActionOn(element: E): Any? {
        getArgument<Action<E>>(0).execute(element)
        return mock
    }
}
