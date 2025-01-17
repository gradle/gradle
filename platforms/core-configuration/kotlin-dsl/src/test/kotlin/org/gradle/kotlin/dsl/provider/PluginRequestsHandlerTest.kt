package org.gradle.kotlin.dsl.provider

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.plugin.management.internal.PluginHandler
import org.gradle.plugin.management.internal.PluginRequests
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
        val allPlugins = mock<PluginRequests>(name = "allPlugins")
        val pluginHandler = mock<PluginHandler> {
            on { getAllPluginRequests(initialRequests, target) } doReturn allPlugins
        }
        val pluginRequestApplicator = mock<PluginRequestApplicator>()
        val scriptHandler = mock<ScriptHandlerInternal>()
        val targetScope = mock<ClassLoaderScope>()

        // when:
        val subject = PluginRequestsHandler(pluginRequestApplicator, pluginHandler)
        subject.handle(initialRequests, scriptHandler, target, targetScope)

        // then:
        verify(pluginRequestApplicator).applyPlugins(allPlugins, scriptHandler, pluginManager, targetScope)
    }
}
