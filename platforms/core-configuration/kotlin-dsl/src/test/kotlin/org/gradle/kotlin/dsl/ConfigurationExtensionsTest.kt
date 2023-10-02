package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.KStubbing
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.mockito.ArgumentMatchers.anyString
import spock.lang.Issue
import kotlin.test.Test
import kotlin.test.assertEquals


class ConfigurationExtensionsTest {

    @Test
    fun `given group and module, 'exclude' extension will build corresponding map`() {

        val configuration: Configuration = mock()
        whenever(configuration.exclude(mapOf("group" to "org.gradle", "module" to "test"))).thenReturn(configuration)
        assertThat(
            configuration.exclude(group = "org.gradle", module = "test"),
            sameInstance(configuration)
        )
    }

    private
    inline fun <reified C : Configuration> KStubbing<ConfigurationContainer>.registerMocks(
        crossinline fn: (String, Action<C>) -> NamedDomainObjectProvider<C>,
        crossinline variable: (String) -> NamedDomainObjectProvider<C>
    ) {
        onGeneric { fn(anyString(), any<Action<C>>()) } doAnswer {
            val nameArg: String = it.getArgument(0)
            mock {
                on { name } doAnswer { nameArg }
            }
        }
        on { variable(anyString()) } doAnswer {
            val nameArg: String = it.getArgument(0)
            mock {
                on { name } doAnswer { nameArg }
            }
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/26601")
    fun `configurationContainerScope supports creating resolvable configurations with a given property name`() {

        val configurations: ConfigurationContainer = mock {
            registerMocks(it::resolvable, it::resolvable)
            registerMocks(it::consumable, it::consumable)
            registerMocks(it::dependencyScope, it::dependencyScope)
        }

        val resolvable1 by configurations.resolvable { }
        val resolvable2 by configurations.resolvable
        assertEquals("resolvable1", resolvable1.name)
        assertEquals("resolvable2", resolvable2.name)

        val consumable1 by configurations.consumable { }
        val consumable2 by configurations.consumable
        assertEquals("consumable1", consumable1.name)
        assertEquals("consumable2", consumable2.name)

        val dependencyScope1 by configurations.dependencyScope { }
        val dependencyScope2 by configurations.dependencyScope
        assertEquals("dependencyScope1", dependencyScope1.name)
        assertEquals("dependencyScope2", dependencyScope2.name)
    }
}
