package Gradle_BuildCacheDeactivated

import jetbrains.buildServer.configs.kotlin.v10.project
import jetbrains.buildServer.configs.kotlin.v10.version
import model.*
import projects.RootProject

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

version = "2017.1"
val buildModel = CIBuildModel(
        projectPrefix = "Gradle_BuildCacheDeactivated_",
        rootProjectName = "Build Cache Deactivated",
        masterAndReleaseBranches = listOf("master"),
        parentBuildCache = NoBuildCache,
        childBuildCache = NoBuildCache,
        tagBuilds = false,
        publishStatusToGitHub = false,
        buildScanTags = listOf("BuildCacheDeactivated"),
        stages = listOf(
                Stage("Quick Feedback - Linux Only", "Run checks and functional tests (embedded executer)",
                        specificBuilds = listOf(
                                SpecificBuild.SanityCheck),
                        functionalTests = listOf(
                                TestCoverage(TestType.quick, OS.linux, JvmVersion.java8))),
                Stage("Quick Feedback", "Run checks and functional tests (embedded executer)",
                        trigger = Trigger.eachCommit,
                        functionalTests = listOf(
                                TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)))
        )
)
project(RootProject(buildModel))
