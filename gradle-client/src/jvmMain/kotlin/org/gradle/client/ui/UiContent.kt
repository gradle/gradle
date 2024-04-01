package org.gradle.client.ui

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.stack.Children
import org.gradle.client.ui.build.BuildContent
import org.gradle.client.ui.buildlist.BuildListContent
import org.gradle.client.ui.connected.ConnectedContent
import org.gradle.client.ui.theme.GradleClientTheme

@Composable
fun UiContent(uiComponent: UiComponent) {
    GradleClientTheme {
        Children(uiComponent.childStack) {
            when (val child = it.instance) {
                is UiComponent.Child.BuildList -> BuildListContent(child.component)
                is UiComponent.Child.Build -> BuildContent(child.component)
                is UiComponent.Child.Connected -> ConnectedContent(child.component)
            }
        }
    }
}
