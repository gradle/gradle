package org.gradle.client.ui

import androidx.compose.ui.test.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.items
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.gradle.client.core.Constants.APPLICATION_DISPLAY_NAME
import org.gradle.client.core.database.BuildsRepository
import org.gradle.client.core.database.sqldelight.ApplicationDatabaseFactory
import org.gradle.client.core.database.sqldelight.SqlDriverFactory
import org.gradle.client.ui.connected.ConnectionModel
import org.gradle.client.ui.connected.Outcome
import org.gradle.client.ui.fixtures.AbstractUiTest
import org.gradle.tooling.model.gradle.GradleBuild
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GradleClientUiTest : AbstractUiTest() {

    @Test
    fun gradleClientTest() = runTest {
        runDesktopComposeUiTest(800, 600) {
            appDirs.logApplicationDirectories()
            val sqlDriverFactory = SqlDriverFactory(appDirs)
            val sqlDriver = sqlDriverFactory.createDriver()
            val lifecycle = LifecycleRegistry()
            try {
                val testDispatcher = StandardTestDispatcher(testScheduler)

                val database = ApplicationDatabaseFactory().createDatabase(sqlDriver)
                val buildsRepository = BuildsRepository(
                    queries = database.buildsQueries,
                    readDispatcher = testDispatcher,
                    writeDispatcher = testDispatcher,
                )

                val uiComponent = runOnUiThread {
                    UiComponent(
                        context = DefaultComponentContext(lifecycle = lifecycle),
                        appDispatchers = AppDispatchers(
                            main = testDispatcher,
                            io = testDispatcher,
                        ),
                        appDirs = appDirs,
                        buildsRepository = buildsRepository,
                    )
                }

                setContent {
                    UiContent(uiComponent)
                }

                // Displays build list
                assertThat(uiComponent.childStack.items.size, equalTo(1))
                assertThat(
                    uiComponent.childStack.items.last().instance,
                    instanceOf(UiComponent.Child.BuildList::class.java)
                )
                onNodeWithText(APPLICATION_DISPLAY_NAME).assertIsDisplayed()
                onNodeWithTag("add_build", useUnmergedTree = true).assertTextEquals("Add build")

                // Add a build
                val buildRootDir = tmpDir.root.resolve("some-build").apply {
                    mkdirs()
                    resolve("settings.gradle").writeText("""rootProject.name = "some-root"""")
                }
                val buildList = uiComponent.childStack.items.single().instance as UiComponent.Child.BuildList
                buildList.component.onNewBuildRootDirChosen(buildRootDir)
                advanceUntilIdle()
                takeScreenshot("01_build_list")

                // Open build
                onNodeWithText("some-build").performClick()

                // Displays build
                assertThat(uiComponent.childStack.items.size, equalTo(2))
                assertThat(
                    uiComponent.childStack.items.last().instance,
                    instanceOf(UiComponent.Child.Build::class.java)
                )
                advanceUntilIdle()
                onNodeWithText(buildRootDir.absolutePath).assertIsDisplayed()
                takeScreenshot("02_build")

                // Connects to build
                onNodeWithText("Connect").performClick()

                // Displays connected actions
                assertThat(uiComponent.childStack.items.size, equalTo(3))
                assertThat(
                    uiComponent.childStack.items.last().instance,
                    instanceOf(UiComponent.Child.Connected::class.java)
                )
                val connected = uiComponent.childStack.items.last().instance as UiComponent.Child.Connected
                assertThat(
                    connected.component.model.value,
                    instanceOf(ConnectionModel.Connecting::class.java)
                )
                advanceUntilIdle()
                connected.component.modelActions.map { it.displayName }.forEach { actionName ->
                    onNodeWithText(actionName).assertIsDisplayed()
                }
                takeScreenshot("03_connected")

                // Triggers action
                assertThat(
                    (connected.component.model.value as ConnectionModel.Connected).outcome,
                    instanceOf(Outcome.None::class.java)
                )
                onNodeWithText(
                    connected.component.modelActions.single { it.modelType == GradleBuild::class }.displayName
                ).performClick()

                // Displays model
                advanceUntilIdle()
                onNodeWithText("Root Project Name: some-root").assertIsDisplayed()
                takeScreenshot("04_model")

            } finally {
                lifecycle.destroy()
                sqlDriverFactory.stopDriver(sqlDriver)
            }
        }
    }
}
