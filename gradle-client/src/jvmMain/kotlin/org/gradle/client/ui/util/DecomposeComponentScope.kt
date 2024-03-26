package org.gradle.client.ui.util

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.gradle.client.ui.AppDispatchers

fun ComponentContext.componentScope(): CoroutineScope =
    coroutineScope(AppDispatchers.main + SupervisorJob())
