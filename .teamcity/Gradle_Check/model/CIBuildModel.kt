package model

import configurations.*
import jetbrains.buildServer.configs.kotlin.v10.BuildType

data class CIBuildModel (
        val projectPrefix: String = "Gradle_Check_",
        val rootProjectName: String = "Check",
        val tagBuilds: Boolean = true,
        val publishStatusToGitHub: Boolean = true,
        val masterAndReleaseBranches: List<String> = listOf("master", "release"),
        val parentBuildCache: BuildCache = RemoteBuildCache("%gradle.cache.remote.url%"),
        val childBuildCache: BuildCache = RemoteBuildCache("%gradle.cache.remote.url%"),
        val buildScanTags: List<String> = emptyList(),
        val stages: List<Stage> = listOf(
            Stage("Quick Feedback - Linux Only", "Run checks and functional tests (embedded executer)",
                    specificBuilds = listOf(
                            SpecificBuild.SanityCheck),
                    functionalTests = listOf(
                            TestCoverage(TestType.quick, OS.linux, JvmVersion.java8))),
            Stage("Quick Feedback", "Run checks and functional tests (embedded executer)",
                    functionalTests = listOf(
                            TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)),
                    functionalTestsDependOnSpecificBuilds = true),
            Stage("Branch Build Accept", "Run performance and functional tests (against distribution)",
                    specificBuilds = listOf(
                            SpecificBuild.BuildDistributions,
                            SpecificBuild.Gradleception,
                            SpecificBuild.SmokeTests),
                    functionalTests = listOf(
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.platform, OS.windows, JvmVersion.java8)),
                    performanceTests = listOf(PerformanceTestType.test)),
            Stage("Master Accept", "Rerun tests in different environments / 3rd party components",
                    trigger = Trigger.eachCommit,
                    functionalTests = listOf(
                            TestCoverage(TestType.quickFeedbackCrossVersion, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.quickFeedbackCrossVersion, OS.windows, JvmVersion.java7),
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java9),
                            TestCoverage(TestType.parallel, OS.linux, JvmVersion.java7, JvmVendor.ibm))),
            Stage("Release Accept", "Once a day: Rerun tests in more environments",
                    trigger = Trigger.daily,
                    functionalTests = listOf(
                            TestCoverage(TestType.soak, OS.linux, JvmVersion.java8),
                            TestCoverage(TestType.soak, OS.windows, JvmVersion.java8),
                            TestCoverage(TestType.allVersionsCrossVersion, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.allVersionsCrossVersion, OS.windows, JvmVersion.java7),
                            TestCoverage(TestType.noDaemon, OS.linux, JvmVersion.java8),
                            TestCoverage(TestType.noDaemon, OS.windows, JvmVersion.java8),
                            TestCoverage(TestType.platform, OS.macos, JvmVersion.java8)),
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java10)
                    performanceTests = listOf(
                            PerformanceTestType.experiment)),
            Stage("Historical Performance", "Once a week: Run performance tests for multiple Gradle versions",
                    trigger = Trigger.weekly,
                    performanceTests = listOf(
                            PerformanceTestType.historical)))
    ) {

    val subProjects = listOf(
            GradleSubproject("announce"),
            GradleSubproject("antlr"),
            GradleSubproject("baseServices"),
            GradleSubproject("baseServicesGroovy", functionalTests = false),
            GradleSubproject("buildCache"),
            GradleSubproject("buildCacheHttp"),
            GradleSubproject("buildComparison"),
            GradleSubproject("buildOption"),
            GradleSubproject("buildInit"),
            GradleSubproject("cli", functionalTests = false),
            GradleSubproject("codeQuality"),
            GradleSubproject("compositeBuilds"),
            GradleSubproject("core", crossVersionTests = true),
            GradleSubproject("coreApi", functionalTests = false),
            GradleSubproject("dependencyManagement", crossVersionTests = true),
            GradleSubproject("diagnostics"),
            GradleSubproject("ear"),
            GradleSubproject("ide", crossVersionTests = true),
            GradleSubproject("ideNative"),
            GradleSubproject("idePlay"),
            GradleSubproject("integTest", crossVersionTests = true),
            GradleSubproject("internalIntegTesting", functionalTests = false),
            GradleSubproject("internalPerformanceTesting"),
            GradleSubproject("internalTesting", functionalTests = false),
            GradleSubproject("ivy", crossVersionTests = true),
            GradleSubproject("jacoco"),
            GradleSubproject("javascript"),
            GradleSubproject("jvmServices", functionalTests = false),
            GradleSubproject("languageGroovy"),
            GradleSubproject("languageJava"),
            GradleSubproject("languageJvm"),
            GradleSubproject("languageNative"),
            GradleSubproject("languageScala"),
            GradleSubproject("launcher"),
            GradleSubproject("logging"),
            GradleSubproject("maven", crossVersionTests = true),
            GradleSubproject("messaging"),
            GradleSubproject("modelCore"),
            GradleSubproject("modelGroovy"),
            GradleSubproject("native"),
            GradleSubproject("osgi"),
            GradleSubproject("persistentCache"),
            GradleSubproject("platformBase"),
            GradleSubproject("platformJvm"),
            GradleSubproject("platformNative"),
            GradleSubproject("platformPlay"),
            GradleSubproject("pluginDevelopment"),
            GradleSubproject("pluginUse", crossVersionTests = true),
            GradleSubproject("plugins"),
            GradleSubproject("processServices"),
            GradleSubproject("publish"),
            GradleSubproject("reporting"),
            GradleSubproject("resources"),
            GradleSubproject("resourcesGcs"),
            GradleSubproject("resourcesHttp"),
            GradleSubproject("resourcesS3"),
            GradleSubproject("resourcesSftp"),
            GradleSubproject("scala"),
            GradleSubproject("signing"),
            GradleSubproject("testKit"),
            GradleSubproject("testingBase"),
            GradleSubproject("testingJvm"),
            GradleSubproject("testingJunitPlatform"),
            GradleSubproject("testingNative"),
            GradleSubproject("toolingApi", crossVersionTests = true),
            GradleSubproject("toolingApiBuilders", functionalTests = false),
            GradleSubproject("versionControl"),
            GradleSubproject("workers"),
            GradleSubproject("wrapper", crossVersionTests = true),

            GradleSubproject("soak", unitTests = false, functionalTests = false),

            GradleSubproject("buildScanPerformance", unitTests = false, functionalTests = false),
            GradleSubproject("distributions", unitTests = false, functionalTests = false),
            GradleSubproject("docs", unitTests = false, functionalTests = false),
            GradleSubproject("installationBeacon", unitTests = false, functionalTests = false),
            GradleSubproject("internalAndroidPerformanceTesting", unitTests = false, functionalTests = false),
            GradleSubproject("performance", unitTests = false, functionalTests = false),
            GradleSubproject("runtimeApiInfo", unitTests = false, functionalTests = false),
            GradleSubproject("smokeTest", unitTests = false, functionalTests = false)
    )
}

data class GradleSubproject(val name: String, val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false) {
    fun asDirectoryName(): String {
        return name.replace(Regex("([A-Z])"), { "-" + it.groups[1]!!.value.toLowerCase()})
    }
}

interface BuildCache {
    fun gradleParameters(): List<String>
}

data class RemoteBuildCache(val url: String, val username: String = "%gradle.cache.remote.username%", val password: String = "%gradle.cache.remote.password%") : BuildCache {
    override fun gradleParameters(): List<String> {
        return listOf("--build-cache",
                """"-Dgradle.cache.remote.url=$url"""",
                """"-Dgradle.cache.remote.username=$username"""",
                """"-Dgradle.cache.remote.password=$password""""
        )
    }
}

object NoBuildCache : BuildCache {
    override fun gradleParameters(): List<String> {
        return emptyList()
    }
}

data class Stage(val name: String, val description: String, val specificBuilds: List<SpecificBuild> = emptyList(), val performanceTests: List<PerformanceTestType> = emptyList(), val functionalTests: List<TestCoverage> = emptyList(), val trigger: Trigger = Trigger.never, val functionalTestsDependOnSpecificBuilds: Boolean = false)

data class TestCoverage(val testType: TestType, val os: OS, val version: JvmVersion, val vendor: JvmVendor = JvmVendor.oracle) {
    fun asId(model : CIBuildModel): String {
        return "${model.projectPrefix}${testType.name.capitalize()}_${version.name.capitalize()}_${vendor.name.capitalize()}_${os.name.capitalize()}"
    }

    fun asConfigurationId(model : CIBuildModel, subproject: String = ""): String {
        val shortenedSubprojectName = subproject.replace("internal", "i").replace("Testing", "T")
        return asId(model) + "_" + if (!subproject.isEmpty()) shortenedSubprojectName else "0"
    }

    fun asName(): String {
        return "Test Coverage - ${testType.name.capitalize()} ${version.name.capitalize()} ${vendor.name.capitalize()} ${os.name.capitalize()}"
    }
}

enum class OS(val agentRequirement: String, val ignoredSubprojects: List<String> = emptyList()) {
    linux("Linux"), windows("Windows"), macos("Mac", listOf("integTest", "native", "plugins", "resources", "scala", "workers", "wrapper"))
}

enum class JvmVersion {
    java7, java8, java9, java10
}

enum class TestType(val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false) {
    quick(true, true, false), platform(true, true, false),
    quickFeedbackCrossVersion(false, false, true), allVersionsCrossVersion(false, false, true),
    parallel(false, true, false), noDaemon(false, true, false),
    soak(false, false, false)
}

enum class JvmVendor {
    oracle, ibm
}

enum class PerformanceTestType(val taskId: String, val timeout : Int, val defaultBaselines: String = "", val extraParameters : String = "") {
    test("PerformanceTest", 420, "defaults"),
    experiment("PerformanceExperiment", 420, "defaults"),
    historical("FullPerformanceTest", 2280, "2.9,2.12,2.14.1,last", "--checks none");

    fun asId(model : CIBuildModel): String {
        return "${model.projectPrefix}Performance${name.capitalize()}Coordinator"
    }
}

enum class Trigger {
    never, eachCommit, daily, weekly
}

enum class SpecificBuild {
    SanityCheck {
        override fun create(model: CIBuildModel): BuildType {
            return SanityCheck(model)
        }
    },
    BuildDistributions {
        override fun create(model: CIBuildModel): BuildType {
            return BuildDistributions(model)
        }

    },
    Gradleception {
        override fun create(model: CIBuildModel): BuildType {
            return Gradleception(model)
        }

    },
    SmokeTests {
        override fun create(model: CIBuildModel): BuildType {
            return SmokeTests(model)
        }
    },
    DependenciesCheck {
        override fun create(model: CIBuildModel): BuildType {
            return DependenciesCheck(model)
        }
    };

    abstract fun create(model: CIBuildModel): BuildType
}
