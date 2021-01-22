package Gradle_Check

import Gradle_Check.model.JsonBasedGradleSubprojectProvider
import Gradle_Check.model.StatisticBasedFunctionalTestBucketProvider
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version
import model.CIBuildModel
import projects.RootProject
import java.io.File

/*
The settings script is an entry point for defining a single
TeamCity project. TeamCity looks for the 'settings.kts' file in a
project directory and runs it if it's found, so the script name
shouldn't be changed and its package should be the same as the
project's external id.

The script should contain a single call to the project() function
with a Project instance or an init function as an argument, you
can also specify both of them to refine the specified Project in
the init function.

VcsRoots, BuildTypes, and Templates of this project must be
registered inside project using the vcsRoot(), buildType(), and
template() methods respectively.

Subprojects can be defined either in their own settings.kts or by
calling the subProjects() method in this project.
*/

version = "2020.2"
val model = CIBuildModel(buildScanTags = listOf("Check"), subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json")))
val gradleBuildBucketProvider = StatisticBasedFunctionalTestBucketProvider(model, File("./test-class-data.json"))
project(RootProject(model, gradleBuildBucketProvider))
