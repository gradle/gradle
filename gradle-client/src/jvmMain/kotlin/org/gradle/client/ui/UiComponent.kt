package org.gradle.client.ui

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import org.gradle.client.ui.build.BuildComponent
import org.gradle.client.ui.welcome.WelcomeComponent

class UiComponent(
    context: ComponentContext
) : ComponentContext by context {

    sealed interface Child {
        class Welcome(val component: WelcomeComponent) : Child
        class Build(val component: BuildComponent) : Child
    }

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Welcome,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.Welcome -> Child.Welcome(
                WelcomeComponent(
                    context = context,
                    onBuildSelected = { id -> navigation.push(Config.Build(id)) }
                )
            )

            is Config.Build -> Child.Build(
                BuildComponent(
                    context = context,
                    id = config.id,
                    onFinished = { navigation.pop() }
                )
            )
        }

    @Serializable
    private sealed class Config {
        @Serializable
        data object Welcome : Config()

        @Serializable
        data class Build(val id: String) : Config()
    }
}
