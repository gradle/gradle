package org.gradle.client.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.gradle.client.core.files.AppDirs
import org.gradle.client.ui.connected.ConnectionModel
import org.gradle.client.ui.connected.Outcome
import org.gradle.client.ui.fixtures.TestAppDirs
import org.gradle.tooling.model.gradle.GradleBuild
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GradleClientUiTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @get:Rule
    val rule = createComposeRule()

    private val appDirs: AppDirs by lazy {
        TestAppDirs(tmpDir.root.resolve("appDirs"))
    }

    @Test
    fun gradleClientTest() = runTest {
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

            val uiComponent = UiComponent(
                context = DefaultComponentContext(lifecycle = lifecycle),
                appDispatchers = AppDispatchers(
                    main = testDispatcher,
                    io = testDispatcher,
                ),
                appDirs = appDirs,
                buildsRepository = buildsRepository,
            )

            rule.setContent {
                UiContent(uiComponent)
            }

            // Displays build list
            assertThat(uiComponent.childStack.items.size, equalTo(1))
            assertThat(
                uiComponent.childStack.items.last().instance,
                instanceOf(UiComponent.Child.BuildList::class.java)
            )
            rule.onNodeWithText(APPLICATION_DISPLAY_NAME).assertIsDisplayed()
            rule.onNodeWithTag("add_build", useUnmergedTree = true).assertTextEquals("Add build")

            // Add a build
            val buildRootDir = tmpDir.root.resolve("some-build").apply {
                mkdirs()
                resolve("settings.gradle").writeText(
                    """
                    rootProject.name = "some-root"
                    """.trimIndent()
                )
            }
            val buildList = uiComponent.childStack.items.single().instance as UiComponent.Child.BuildList
            buildList.component.onNewBuildRootDirChosen(buildRootDir)
            advanceUntilIdle()
            rule.onNodeWithText("some-build").performClick()

            // Displays build
            assertThat(uiComponent.childStack.items.size, equalTo(2))
            assertThat(
                uiComponent.childStack.items.last().instance,
                instanceOf(UiComponent.Child.Build::class.java)
            )
            advanceUntilIdle()
            rule.onNodeWithText(buildRootDir.absolutePath).assertIsDisplayed()

            // Connects to build
            rule.onNodeWithText("Connect").performClick()

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
                rule.onNodeWithText(actionName).assertIsDisplayed()
            }

            // Triggers action
            assertThat(
                (connected.component.model.value as ConnectionModel.Connected).outcome,
                instanceOf(Outcome.None::class.java)
            )
            rule.onNodeWithText(
                connected.component.modelActions.single { it.modelType == GradleBuild::class }.displayName
            ).performClick()

            // Displays model
            advanceUntilIdle()
            rule.onNodeWithText("Root Project Name: some-root").assertIsDisplayed()

        } finally {
            lifecycle.destroy()
            sqlDriverFactory.stopDriver(sqlDriver)
        }
    }
}
