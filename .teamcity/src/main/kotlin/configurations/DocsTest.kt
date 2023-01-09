package configurations

import common.JvmCategory
import common.Os
import common.applyDefaultSettings
import common.toCapitalized
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage
import model.TestSplitType
import model.TestSplitType.EXCLUDE
import model.TestSplitType.INCLUDE
import model.prepareTestClassesStep

fun asDocsTestId(model: CIBuildModel, os: Os): String {
    return "${model.projectId}_DocsTest_${os.asName()}"
}

class DocsTestProject(
    model: CIBuildModel,
    stage: Stage,
    val os: Os,
    testJava: JvmCategory,
    testTypes: List<DocsTestType>
) : Project({
    id(asDocsTestId(model, os))
    name = "Docs Test - ${testJava.version.name.toCapitalized()} ${os.asName()}"
}) {
    val docsTests: List<BaseGradleBuildType>

    init {
        docsTests = testTypes.flatMap {
            listOf(
                DocsTest(model, stage, os, testJava, 1, it, INCLUDE, listOf("org.gradle.docs.samples.Bucket1SnippetsTest=docsTest")),
                DocsTest(model, stage, os, testJava, 2, it, INCLUDE, listOf("org.gradle.docs.samples.Bucket2SnippetsTest=docsTest")),
                DocsTest(model, stage, os, testJava, 3, it, INCLUDE, listOf("org.gradle.docs.samples.Bucket3SnippetsTest=docsTest")),
                DocsTest(
                    model, stage, os, testJava, 4, it, EXCLUDE,
                    listOf(
                        "org.gradle.docs.samples.Bucket1SnippetsTest=docsTest",
                        "org.gradle.docs.samples.Bucket2SnippetsTest=docsTest",
                        "org.gradle.docs.samples.Bucket3SnippetsTest=docsTest"
                    )
                )
            )
        }

        docsTests.forEach(this::buildType)
    }
}

class DocsTestTrigger(model: CIBuildModel, docsTestProject: DocsTestProject) : BaseGradleBuildType(init = {
    id("${asDocsTestId(model, docsTestProject.os)}_Trigger")
    name = docsTestProject.name + " (Trigger)"
    type = Type.COMPOSITE

    applyDefaultSettings()

    features {
        publishBuildStatusToGithub(model)
    }
    dependencies {
        snapshotDependencies(docsTestProject.docsTests)
    }
})

enum class DocsTestType(val ccEnabled: Boolean, val docsTestName: String, val docsTestDesc: String) {
    CONFIG_CACHE_ENABLED(true, "ConfigCacheDocsTest", "Docs Test With Config Cache Enabled"),
    CONFIG_CACHE_DISABLED(false, "DocsTest", "Docs Test"),
}

class DocsTest(
    model: CIBuildModel,
    stage: Stage,
    os: Os,
    testJava: JvmCategory,
    index: Int,
    docsTestType: DocsTestType,
    testSplitType: TestSplitType,
    testClasses: List<String>,
) : BaseGradleBuildType(stage = stage, init = {

    id("${model.projectId}_${docsTestType.docsTestName}_${testJava.version.name.toCapitalized()}_${os.asName()}_$index")
    name = "${docsTestType.docsTestDesc} - ${testJava.version.name.toCapitalized()} ${os.asName()} ($index)"

    features {
        publishBuildStatusToGithub(model)
    }

    applyTestDefaults(
        model,
        this,
        "docs:docsTest${if (testSplitType == EXCLUDE) " docs:checkSamples" else ""}",
        os = os,
        timeout = 60,
        extraParameters = buildScanTag(docsTestType.docsTestName) +
            " -PenableConfigurationCacheForDocsTests=${docsTestType.ccEnabled}" +
            " -PtestJavaVersion=${testJava.version.major}" +
            " -PtestJavaVendor=${testJava.vendor.name}" +
            " -P${testSplitType.name.lowercase()}TestClasses=true",
        preSteps = prepareTestClassesStep(os, testSplitType, testClasses)
    )
})
