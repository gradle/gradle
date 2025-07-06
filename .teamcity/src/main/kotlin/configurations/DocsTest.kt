package configurations

import common.JvmCategory
import common.Os
import common.applyDefaultSettings
import common.buildScanTagParam
import configurations.ParallelizationMethod.TeamCityParallelTests
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildFeatures.parallelTests
import model.CIBuildModel
import model.Stage

fun asDocsTestId(
    model: CIBuildModel,
    os: Os,
): String = "${model.projectId}_DocsTest_${os.asName()}"

class DocsTestProject(
    model: CIBuildModel,
    stage: Stage,
    val os: Os,
    testJava: JvmCategory,
    testTypes: List<DocsTestType>,
) : Project({
        id(asDocsTestId(model, os))
        name = "Docs Test - ${testJava.version.toCapitalized()} ${os.asName()}"
    }) {
    val docsTests =
        testTypes.map {
            if (os == Os.LINUX) {
                DocsTest(model, stage, os, testJava, it, ParallelizationMethod.TestDistribution.extraBuildParameters)
            } else {
                TeamCityParallelDocsTest(model, stage, os, testJava, it, 4)
            }
        }

    init {
        docsTests.forEach(this::buildType)
    }
}

class DocsTestTrigger(
    model: CIBuildModel,
    docsTestProject: DocsTestProject,
) : OsAwareBaseGradleBuildType(os = docsTestProject.os, init = {
        id("${asDocsTestId(model, docsTestProject.os)}_Trigger")
        name = docsTestProject.name + " (Trigger)"
        type = Type.COMPOSITE

        applyDefaultSettings()

        dependencies {
            snapshotDependencies(docsTestProject.docsTests)
        }
    })

enum class DocsTestType(
    val ccEnabled: Boolean,
    val docsTestName: String,
    val docsTestDesc: String,
) {
    CONFIG_CACHE_ENABLED(true, "ConfigCacheDocsTest", "Docs Test With Config Cache Enabled"),
    CONFIG_CACHE_DISABLED(false, "DocsTest", "Docs Test"),
}

open class DocsTest(
    model: CIBuildModel,
    stage: Stage,
    os: Os,
    testJava: JvmCategory,
    docsTestType: DocsTestType,
    parallelizationParameters: String,
) : OsAwareBaseGradleBuildType(os = os, stage = stage, init = {
        id("${model.projectId}_${docsTestType.docsTestName}_${os.asName()}")
        name = "${docsTestType.docsTestDesc} - ${testJava.version.toCapitalized()} ${os.asName()}"

        applyTestDefaults(
            model,
            this,
            "docs:docsTest docs:checkSamples",
            os = os,
            arch = os.defaultArch,
            timeout = 60,
            extraParameters =
                listOf(
                    buildScanTagParam(docsTestType.docsTestName),
                    parallelizationParameters,
                    "-PenableConfigurationCacheForDocsTests=${docsTestType.ccEnabled}",
                    "-PtestJavaVersion=${testJava.version.major}",
                    "-PtestJavaVendor=${testJava.vendor.name.lowercase()}",
                ).joinToString(" "),
        )
    })

class TeamCityParallelDocsTest(
    model: CIBuildModel,
    stage: Stage,
    os: Os,
    testJava: JvmCategory,
    docsTestType: DocsTestType,
    parallelism: Int,
) : DocsTest(
        model,
        stage,
        os,
        testJava,
        docsTestType,
        TeamCityParallelTests(parallelism).extraBuildParameters + " -PteamCityParallelTestsBatch=%teamCityParallelTestsBatch%",
    ) {
    init {
        features {
            parallelTests {
                numberOfBatches = parallelism
            }
        }

        params {
            // Could be "1/1" (for initial run) or "1/4", "2/4", "3/4", "4/4" (for batched run)
            text(
                "teamCityParallelTestsBatch",
                "%teamcity.build.parallelTests.currentBatch%/%teamcity.build.parallelTests.totalBatches%",
                allowEmpty = true,
            )
        }
    }
}
