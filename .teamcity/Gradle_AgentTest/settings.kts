package Gradle_AgentTest

import common.JvmVersion
import common.NoBuildCache
import common.Os
import jetbrains.buildServer.configs.kotlin.v2018_2.project
import jetbrains.buildServer.configs.kotlin.v2018_2.version
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
import model.StageNames
import model.TestCoverage
import model.TestType
import projects.RootProject

/*
The settings script is an entry point for defining a single
TeamCity project. TeamCity looks for the 'settings.kts' file in a
project directory and runs it if it's found, so the script name
shouldn't be changed and its package should be the same as the
project's id.

The script should contain a single call to the project() function
with a Project instance or an init function as an argument.

VcsRoots, BuildTypes, and Templates of this project must be
registered inside project using the vcsRoot(), buildType(), and
template() methods respectively.

Subprojects can be defined either in their own settings.kts or by
calling the subProjects() method in this project.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View ->
Tool Windows -> Maven Projects), find the generate task
node (Plugins -> teamcity-configs -> teamcity-configs:generate),
the 'Debug' option is available in the context menu for the task.
*/

version = "2018.2"
val buildModel = CIBuildModel(
        projectPrefix = "Gradle_AgentTest_",
        rootProjectName = "Test Build Agents",
        masterAndReleaseBranches = listOf("master"),
        parentBuildCache = NoBuildCache,
        childBuildCache = NoBuildCache,
        tagBuilds = false,
        publishStatusToGitHub = false,
        buildScanTags = listOf("AgentTest"),
        stages = listOf(
                Stage(StageNames.QUICK_FEEDBACK_LINUX_ONLY,
                        runsIndependent = true,
                        specificBuilds = listOf(SpecificBuild.CompileAll, SpecificBuild.SanityCheck),
                        functionalTests = listOf(TestCoverage(TestType.quick, Os.linux, JvmVersion.java8)))
        )
)
project(RootProject(buildModel))
