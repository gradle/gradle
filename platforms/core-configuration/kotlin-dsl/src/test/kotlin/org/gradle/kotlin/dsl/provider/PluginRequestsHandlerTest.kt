package org.gradle.kotlin.dsl.provider

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler
import org.gradle.plugin.use.internal.PluginRequestApplicator

import org.junit.Test


class PluginRequestsHandlerTest {

    @Test
    fun `applies plugins after merging auto-applied plugin requests`() {

        // given:
        val pluginManager = mock<PluginManagerInternal>()
        val target = mock<ProjectInternal> {
            on { this.pluginManager } doReturn pluginManager
        }
        val initialRequests = mock<PluginRequests>(name = "initialRequests")
        val autoAppliedPlugins = mock<PluginRequests>(name = "autoAppliedPlugins")
        val autoAppliedPluginHandler = mock<AutoAppliedPluginHandler> {
            on { getAutoAppliedPlugins(initialRequests, target) } doReturn autoAppliedPlugins
        }
        val pluginRequestApplicator = mock<PluginRequestApplicator>()
        val scriptHandler = mock<ScriptHandlerInternal>()
        val targetScope = mock<ClassLoaderScope>()

        // when:
        val subject = PluginRequestsHandler(pluginRequestApplicator, autoAppliedPluginHandler)
        subject.handle(initialRequests, scriptHandler, target, targetScope)

        // then:
        verify(pluginRequestApplicator).applyPlugins(initialRequests, autoAppliedPlugins, scriptHandler, pluginManager, targetScope)
    }
}
