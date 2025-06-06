package configurations

import common.JvmCategory
import common.Os
import common.applyDefaultSettings
import common.buildScanTagParam
import common.toCapitalized
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
    val docsTests: List<BaseGradleBuildType>

    init {
        docsTests = testTypes.map { DocsTest(model, stage, os, testJava, it, parallelism = 4) }
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

class DocsTest(
    model: CIBuildModel,
    stage: Stage,
    os: Os,
    testJava: JvmCategory,
    docsTestType: DocsTestType,
    parallelism: Int = 1,
) : OsAwareBaseGradleBuildType(os = os, stage = stage, init = {
        id("${model.projectId}_${docsTestType.docsTestName}_${os.asName()}")
        name = "${docsTestType.docsTestDesc} - ${testJava.version.toCapitalized()} ${os.asName()}"

        if (parallelism > 1) {
            features {
                parallelTests {
                    this.numberOfBatches = parallelism
                }
            }
        }

        applyTestDefaults(
            model,
            this,
            "docs:docsTest docs:checkSamples",
            os = os,
            arch = os.defaultArch,
            timeout = 60,
            extraParameters =
                buildScanTagParam(docsTestType.docsTestName) +
                    " -PenableConfigurationCacheForDocsTests=${docsTestType.ccEnabled}" +
                    " -PtestJavaVersion=${testJava.version.major}" +
                    " -PtestJavaVendor=${testJava.vendor.name.lowercase()}",
        )
    })
