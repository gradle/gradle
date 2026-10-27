package org.gradle.kotlin.dsl

import org.gradle.api.artifacts.Configuration

import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever


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
}
