package configurations

import common.JvmCategory
import common.Os
import common.applyDefaultSettings
import common.buildScanTagParam
import common.toCapitalized
import configurations.TestSplitType.EXCLUDE
import configurations.TestSplitType.INCLUDE
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

enum class TestSplitType(
    val action: String,
) {
    INCLUDE("include"),
    EXCLUDE("exclude"),
}

fun prepareTestClassesStep(
    os: Os,
    type: TestSplitType,
    testClasses: List<String>,
): BuildSteps.() -> Unit {
    val action = type.action
    val unixScript = """
mkdir -p test-splits
rm -rf test-splits/*-test-classes.properties
cat > test-splits/$action-test-classes.properties << EOL
${testClasses.joinToString("\n")}
EOL
echo "Tests to be ${action}d in this build"
cat test-splits/$action-test-classes.properties
"""

    val linesWithEcho = testClasses.joinToString("\n") { "echo $it" }

    val windowsScript = """
mkdir test-splits
del /f /q test-splits\include-test-classes.properties
del /f /q test-splits\exclude-test-classes.properties
(
$linesWithEcho
) > test-splits\$action-test-classes.properties
echo "Tests to be ${action}d in this build"
type test-splits\$action-test-classes.properties
"""

    return {
        script {
            name = "PREPARE_TEST_CLASSES"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = if (os == Os.WINDOWS) windowsScript else unixScript
        }
    }
}

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
        docsTests =
            testTypes.flatMap {
                listOf(
                    DocsTest(model, stage, os, testJava, 1, it, INCLUDE, listOf("org.gradle.docs.samples.Bucket1SnippetsTest=docsTest")),
                    DocsTest(model, stage, os, testJava, 2, it, INCLUDE, listOf("org.gradle.docs.samples.Bucket2SnippetsTest=docsTest")),
                    DocsTest(model, stage, os, testJava, 3, it, INCLUDE, listOf("org.gradle.docs.samples.Bucket3SnippetsTest=docsTest")),
                    DocsTest(
                        model,
                        stage,
                        os,
                        testJava,
                        4,
                        it,
                        EXCLUDE,
                        listOf(
                            "org.gradle.docs.samples.Bucket1SnippetsTest=docsTest",
                            "org.gradle.docs.samples.Bucket2SnippetsTest=docsTest",
                            "org.gradle.docs.samples.Bucket3SnippetsTest=docsTest",
                        ),
                    ),
                )
            }

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
    index: Int,
    docsTestType: DocsTestType,
    testSplitType: TestSplitType,
    testClasses: List<String>,
) : OsAwareBaseGradleBuildType(os = os, stage = stage, init = {

        id("${model.projectId}_${docsTestType.docsTestName}_${os.asName()}_$index")
        name = "${docsTestType.docsTestDesc} - ${testJava.version.toCapitalized()} ${os.asName()} ($index)"

        applyTestDefaults(
            model,
            this,
            "docs:docsTest${if (testSplitType == EXCLUDE) " docs:checkSamples" else ""}",
            os = os,
            arch = os.defaultArch,
            timeout = 60,
            extraParameters =
                buildScanTagParam(docsTestType.docsTestName) +
                    " -PenableConfigurationCacheForDocsTests=${docsTestType.ccEnabled}" +
                    " -PtestJavaVersion=${testJava.version.major}" +
                    " -PtestJavaVendor=${testJava.vendor.name.lowercase()}" +
                    " -P${testSplitType.name.lowercase()}TestClasses=true",
            preSteps = prepareTestClassesStep(os, testSplitType, testClasses),
        )
    })
