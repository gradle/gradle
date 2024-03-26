/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.ide.sync

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Path

/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


object IdeStarterIntegration {

    fun configureIdeHome(ideHome: Path) {
        di = DI {
            extend(di)
            bindSingleton<GlobalPaths>(overrides = true) {
                object : GlobalPaths(ideHome) {
                    override val testHomePath: Path
                        get() = ideHome
                }
            }
        }
    }

    fun runSync(testContext: IDETestContext) {
        testContext.runIDE(
            commandLine = { IDECommandLine.OpenTestCaseProject(testContext) },
            commands = listOf(CommandChain().exitApp())
        )
    }

    fun getIdeaCommunity(buildType: String, version: String): IdeInfo =
        IdeProductProvider.IC.copy(
            buildType = buildType,
            version = version
        )

    fun getLocalProject(projectPath: Path): ProjectInfoSpec =
        LocalProjectInfo(
            projectDir = projectPath,
            description = "Project under test"
        )
}
