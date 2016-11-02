package Gradle_Branches_Performance

import Gradle_Branches_Performance.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project
import jetbrains.buildServer.configs.kotlin.v10.ProjectFeature
import jetbrains.buildServer.configs.kotlin.v10.ProjectFeature.*

object Project : Project({
    uuid = "bec27f69-1287-43e6-a798-e72b68a396dd"
    extId = "Gradle_Branches_Performance"
    parentId = "Gradle_Branches"
    name = "Performance"
    description = "Configurations that execute performance tests (to be run exclusively on a given machine)"

    buildType(Gradle_Branches_Performance_IndividualPerformanceScenarioWorkersLinux)
    buildType(Gradle_Branches_Performance_PerformanceExperimentsCoordinatorLinux)
    buildType(Gradle_Branches_Performance_AdHocPerformanceScenarioLinux)
    buildType(Gradle_Branches_Performance_PerformanceRegressionAnalyzerLinux)
    buildType(Gradle_Branches_Performance_PerformanceHistoricalBuildLinux)
    buildType(Gradle_Branches_Performance_PerformanceTestCoordinatorLinux)

    features {
        feature {
            id = "PROJECT_EXT_60"
            type = "ReportTab"
            param("startPage", "results/performance/build/performance-tests/report/index.html")
            param("title", "Performance")
            param("type", "BuildReportTab")
        }
        feature {
            id = "PROJECT_EXT_61"
            type = "ReportTab"
            param("buildTypeId", "Gradle_Branches_Performance_PerformanceTestCoordinatorLinux")
            param("revisionRuleName", "lastFinished")
            param("revisionRuleRevision", "latest.lastFinished")
            param("startPage", "results/performance/build/performance-tests/report/index.html")
            param("title", "Performance")
            param("type", "ProjectReportTab")
        }
    }

    cleanup {
        artifacts(days = 7)
    }
})
