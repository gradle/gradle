package model

import configurations.BuildDistributions
import configurations.DependenciesCheck
import configurations.Gradleception
import configurations.SanityCheck
import configurations.SmokeTests
import jetbrains.buildServer.configs.kotlin.v2018_1.BuildType

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
                            TestCoverage(TestType.quick, OS.linux, JvmVersion.java8)), omitsSlowProjects = true),
            Stage("Quick Feedback", "Run checks and functional tests (embedded executer)",
                    functionalTests = listOf(
                            TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)),
                    functionalTestsDependOnSpecificBuilds = true,
                    omitsSlowProjects = true),
            Stage("Branch Build Accept", "Run performance and functional tests (against distribution)",
                    specificBuilds = listOf(
                            SpecificBuild.BuildDistributions,
                            SpecificBuild.Gradleception,
                            SpecificBuild.SmokeTests),
                    functionalTests = listOf(
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.platform, OS.windows, JvmVersion.java8)),
                    performanceTests = listOf(PerformanceTestType.test),
                    omitsSlowProjects = true),
            Stage("Master Accept", "Rerun tests in different environments / 3rd party components",
                    trigger = Trigger.eachCommit,
                    functionalTests = listOf(
                            TestCoverage(TestType.quickFeedbackCrossVersion, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.quickFeedbackCrossVersion, OS.windows, JvmVersion.java7),
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java10),
                            TestCoverage(TestType.parallel, OS.linux, JvmVersion.java7, JvmVersion.java8, JvmVendor.ibm))
            ),
            Stage("Release Accept", "Once a day: Rerun tests in more environments",
                    trigger = Trigger.daily,
                    functionalTests = listOf(
                            TestCoverage(TestType.soak, OS.linux, JvmVersion.java8),
                            TestCoverage(TestType.soak, OS.windows, JvmVersion.java8),
                            TestCoverage(TestType.allVersionsCrossVersion, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.allVersionsCrossVersion, OS.windows, JvmVersion.java7),
                            TestCoverage(TestType.noDaemon, OS.linux, JvmVersion.java8),
                            TestCoverage(TestType.noDaemon, OS.windows, JvmVersion.java8),
                            TestCoverage(TestType.platform, OS.macos, JvmVersion.java8),
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java9)),
                    performanceTests = listOf(
                            PerformanceTestType.experiment)),
            Stage("Historical Performance", "Once a week: Run performance tests for multiple Gradle versions",
                    trigger = Trigger.weekly,
                    performanceTests = listOf(
                            PerformanceTestType.historical)),
            Stage("Experimental", "On demand: Run experimental tests",
                    trigger = Trigger.never,
                    runsIndependent = true,
                    functionalTests = listOf(TestCoverage(TestType.platform, OS.linux, JvmVersion.java11)))
        ),
        val subProjects : List<GradleSubproject> = listOf(
            GradleSubproject("announce"),
            GradleSubproject("antlr"),
            GradleSubproject("baseServices"),
            GradleSubproject("baseServicesJava9"),
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
            GradleSubproject("internalIntegTesting"),
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
            GradleSubproject("platformPlay", containsSlowTests = true),
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
            GradleSubproject("toolingApi", crossVersionTests = true, useDaemon = false),
            GradleSubproject("toolingApiBuilders", functionalTests = false),
            GradleSubproject("versionControl"),
            GradleSubproject("workers"),
            GradleSubproject("wrapper", crossVersionTests = true, useDaemon = false),

            GradleSubproject("soak", unitTests = false, functionalTests = false),

            GradleSubproject("distributionsDependencies", unitTests = false, functionalTests = false),
            GradleSubproject("buildScanPerformance", unitTests = false, functionalTests = false),
            GradleSubproject("distributions", unitTests = false, functionalTests = false),
            GradleSubproject("docs", unitTests = false, functionalTests = false),
            GradleSubproject("installationBeacon", unitTests = false, functionalTests = false),
            GradleSubproject("internalAndroidPerformanceTesting", unitTests = false, functionalTests = false),
            GradleSubproject("performance", unitTests = false, functionalTests = false),
            GradleSubproject("runtimeApiInfo", unitTests = false, functionalTests = false),
            GradleSubproject("smokeTest", unitTests = false, functionalTests = false))
        )

data class GradleSubproject(val name: String, val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val containsSlowTests: Boolean = false, val useDaemon: Boolean = true) {
    fun asDirectoryName(): String {
        return name.replace(Regex("([A-Z])"), { "-" + it.groups[1]!!.value.toLowerCase() })
    }

    fun hasTestsOf(type: TestType) = (unitTests && type.unitTests) || (functionalTests && type.functionalTests) || (crossVersionTests && type.crossVersionTests)

    fun useDaemonFor(type: TestType) = useDaemon && type != TestType.noDaemon
}

interface BuildCache {
    fun gradleParameters(os: OS): List<String>
}

data class RemoteBuildCache(val url: String, val username: String = "%gradle.cache.remote.username%", val password: String = "%gradle.cache.remote.password%") : BuildCache {
    override fun gradleParameters(os: OS): List<String> {
        return listOf("--build-cache",
                os.escapeKeyValuePair("-Dgradle.cache.remote.url", url),
                os.escapeKeyValuePair("-Dgradle.cache.remote.username", username),
                os.escapeKeyValuePair("-Dgradle.cache.remote.password", password)
        )
    }
}

private
fun OS.escapeKeyValuePair(key: String, value: String) = if (this == OS.windows) """$key="$value"""" else """"$key=$value""""

object NoBuildCache : BuildCache {
    override fun gradleParameters(os: OS): List<String> {
        return emptyList()
    }
}

data class Stage(val name: String, val description: String, val specificBuilds: List<SpecificBuild> = emptyList(), val performanceTests: List<PerformanceTestType> = emptyList(), val functionalTests: List<TestCoverage> = emptyList(), val trigger: Trigger = Trigger.never, val functionalTestsDependOnSpecificBuilds: Boolean = false, val runsIndependent: Boolean = false, val omitsSlowProjects : Boolean = false) {
    val id = name.replace(" ", "").replace("-", "")
}

data class TestCoverage(val testType: TestType, val os: OS, val testJvmVersion: JvmVersion, val buildJvmVersion: JvmVersion = JvmVersion.java8, val vendor: JvmVendor = JvmVendor.oracle) {
    fun asId(model : CIBuildModel): String {
        if(buildJvmVersion != JvmVersion.java8 && testType == TestType.quickFeedbackCrossVersion) {
            // This is a hack for the limitation on long configuration name
            // "Gradle_Check_QuickFeedbackCrossVersion_Java7_Oracle_Linux_Java9_dependencyManagement" is invalid: it is too long. ID should start with a latin letter and contain only latin letters, digits and underscores (at most 80 characters).
            return "${model.projectPrefix}QkFdbkCrsVsn_${testJvmVersion.name.capitalize()}_${vendor.name.capitalize()}_${os.name.capitalize()}" + suffix()
        }
        if(buildJvmVersion != JvmVersion.java8 && testType == TestType.allVersionsCrossVersion) {
            // This is a hack for the limitation on long configuration name
            // "Gradle_Check_AllVersionsCrossVersion_Java7_Oracle_Windows_Java9_dependencyManagement" is invalid: it is too long. ID should start with a latin letter and contain only latin letters, digits and underscores (at most 80 characters).
            return "${model.projectPrefix}AllVsnCrsVsn_${testJvmVersion.name.capitalize()}_${vendor.name.capitalize()}_${os.name.capitalize()}" + suffix()
        }
        return "${model.projectPrefix}${testType.name.capitalize()}_${testJvmVersion.name.capitalize()}_${vendor.name.capitalize()}_${os.name.capitalize()}" + suffix()
    }

    fun asConfigurationId(model : CIBuildModel, subproject: String = ""): String {
        val shortenedSubprojectName = subproject.replace("internal", "i").replace("Testing", "T")
        return asId(model) + "_" + if (!subproject.isEmpty()) shortenedSubprojectName else "0" + suffix()
    }

    fun asName(): String {
        return "Test Coverage - ${testType.name.capitalize()} ${testJvmVersion.name.capitalize()} ${vendor.name.capitalize()} ${os.name.capitalize()}" + suffix()
    }

    fun suffix(): String {
        return if(buildJvmVersion != JvmVersion.java8) "_${buildJvmVersion.name.capitalize()}" else ""
    }
}

enum class OS(val agentRequirement: String, val ignoredSubprojects: List<String> = emptyList()) {
    linux("Linux"), windows("Windows"), macos("Mac", listOf("integTest", "native", "plugins", "resources", "scala", "workers", "wrapper", "platformPlay"))
}

enum class JvmVersion {
    java7, java8, java9, java10, java11
}

enum class TestType(val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val timeout: Int = 180) {
    quick(true, true, false, 60), platform(true, true, false),
    quickFeedbackCrossVersion(false, false, true), allVersionsCrossVersion(false, false, true),
    parallel(false, true, false), noDaemon(false, true, false, 240),
    soak(false, false, false)
}

enum class JvmVendor {
    oracle, ibm
}

enum class PerformanceTestType(val taskId: String, val timeout : Int, val defaultBaselines: String = "", val extraParameters : String = "") {
    test("PerformanceTest", 420, "defaults"),
    experiment("PerformanceExperiment", 420, "defaults"),
    historical("FullPerformanceTest", 2280, "2.14.1,3.5.1,4.0,last", "--checks none");

    fun asId(model : CIBuildModel): String {
        return "${model.projectPrefix}Performance${name.capitalize()}Coordinator"
    }
}

enum class Trigger {
    never, eachCommit, daily, weekly
}

enum class SpecificBuild {
    SanityCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SanityCheck(model, stage)
        }
    },
    BuildDistributions {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return BuildDistributions(model, stage)
        }

    },
    Gradleception {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return Gradleception(model, stage)
        }

    },
    SmokeTests {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage)
        }
    },
    DependenciesCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return DependenciesCheck(model, stage)
        }
    };

    abstract fun create(model: CIBuildModel, stage: Stage): BuildType
}
