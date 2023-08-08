package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginAware

import org.junit.Test


class PluginAwareExtensionsTest {

    @Test
    fun `non reified apply extension on PluginAware`() {
        assertNonReifiedApplyExtension<PluginAware>()
    }

    @Test
    fun `non reified apply extension on Project`() {
        assertNonReifiedApplyExtension<Project>()
    }

    @Test
    fun `non reified apply extension on Settings`() {
        assertNonReifiedApplyExtension<Settings>()
    }

    @Test
    fun `non reified apply extension on Gradle`() {
        assertNonReifiedApplyExtension<Gradle>()
    }

    @Test
    fun `reified apply extension`() {

        newPluginAwareMock<PluginAware>().run {
            target.apply<AnyPlugin>()
            verify(configurationAction).plugin(AnyPlugin::class.java)
        }
        newPluginAwareMock<Gradle>().run {
            target.apply<GradlePlugin>()
            verify(configurationAction).plugin(GradlePlugin::class.java)
        }
        newPluginAwareMock<Settings>().run {
            target.apply<SettingsPlugin>()
            verify(configurationAction).plugin(SettingsPlugin::class.java)
        }
        newPluginAwareMock<Project>().run {
            target.apply<ProjectPlugin>()
            verify(configurationAction).plugin(ProjectPlugin::class.java)
        }
    }

    @Test
    fun `reified apply to extension`() {
        val arbitraryTarget = "something"

        newPluginAwareMock<PluginAware>().run {
            target.applyTo<AnyPlugin>(arbitraryTarget)
            inOrder(configurationAction) {
                verify(configurationAction).plugin(AnyPlugin::class.java)
                verify(configurationAction).to(arbitraryTarget)
            }
        }
        newPluginAwareMock<Gradle>().run {
            target.applyTo<AnyPlugin>(arbitraryTarget)
            inOrder(configurationAction) {
                verify(configurationAction).plugin(AnyPlugin::class.java)
                verify(configurationAction).to(arbitraryTarget)
            }
        }
        newPluginAwareMock<Settings>().run {
            target.applyTo<AnyPlugin>(arbitraryTarget)
            inOrder(configurationAction) {
                verify(configurationAction).plugin(AnyPlugin::class.java)
                verify(configurationAction).to(arbitraryTarget)
            }
        }
        newPluginAwareMock<Project>().run {
            target.applyTo<AnyPlugin>(arbitraryTarget)
            inOrder(configurationAction) {
                verify(configurationAction).plugin(AnyPlugin::class.java)
                verify(configurationAction).to(arbitraryTarget)
            }
        }
    }
}


private
inline fun <reified T : PluginAware> assertNonReifiedApplyExtension() {

    val arbitraryTarget = "something"

    newPluginAwareMock<T>().run {
        target.apply(from = "script.gradle")
        verify(configurationAction).from("script.gradle")
    }
    newPluginAwareMock<T>().run {
        target.apply(from = "script.gradle", to = arbitraryTarget)
        inOrder(configurationAction) {
            verify(configurationAction).from("script.gradle")
            verify(configurationAction).to(arbitraryTarget)
        }
    }
    newPluginAwareMock<T>().run {
        target.apply(plugin = "some-id")
        verify(configurationAction).plugin("some-id")
    }
    newPluginAwareMock<T>().run {
        target.apply(plugin = "some-id", to = arbitraryTarget)
        inOrder(configurationAction) {
            verify(configurationAction).plugin("some-id")
            verify(configurationAction).to(arbitraryTarget)
        }
    }
    newPluginAwareMock<T>().run {
        target.apply(from = "script.gradle", plugin = "some-id")
        inOrder(configurationAction) {
            verify(configurationAction).plugin("some-id")
            verify(configurationAction).from("script.gradle")
        }
    }
    newPluginAwareMock<T>().run {
        target.apply(from = "script.gradle", plugin = "some-id", to = arbitraryTarget)
        inOrder(configurationAction) {
            verify(configurationAction).plugin("some-id")
            verify(configurationAction).from("script.gradle")
            verify(configurationAction).to(arbitraryTarget)
        }
    }
}


private
class PluginAwareMock<out T : PluginAware>(val target: T, val configurationAction: ObjectConfigurationAction)


private
inline fun <reified T : PluginAware> newPluginAwareMock(): PluginAwareMock<T> {
    val configurationAction = mock<ObjectConfigurationAction>()
    val target = mock<T> {
        on { apply(any<Action<ObjectConfigurationAction>>()) }.then {
            (it.getArgument(0) as Action<ObjectConfigurationAction>).execute(configurationAction)
        }
    }
    return PluginAwareMock(target, configurationAction)
}


private
class GradlePlugin : Plugin<Gradle> {
    override fun apply(target: Gradle) = Unit
}


private
class SettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) = Unit
}


private
class ProjectPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}


private
class AnyPlugin : Plugin<Any> {
    override fun apply(target: Any) = Unit
}
