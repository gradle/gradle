package Gradle_Experimental_NoBuildCache

import common.JvmCategory
import common.NoBuildCache
import common.Os
import jetbrains.buildServer.configs.kotlin.v2018_2.project
import jetbrains.buildServer.configs.kotlin.v2018_2.version
import model.CIBuildModel
import model.Stage
import model.StageNames
import model.TestCoverage
import model.TestType
import model.Trigger
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

version = "2019.1"
project(RootProject(CIBuildModel(
    buildScanTags = listOf("Check"),
    projectPrefix = "Gradle_Experimental_NoBuildCache_",
    parentBuildCache = NoBuildCache,
    childBuildCache = NoBuildCache,
    stages = listOf(
        Stage(StageNames.WINDOWS_10_EVALUATION_QUICK,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(20, TestType.quick, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor))),
        Stage(StageNames.WINDOWS_10_EVALUATION_PLATFORM,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(21, TestType.platform, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)))
    ))))
