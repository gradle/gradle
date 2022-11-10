package configurations

import common.JvmCategory
import common.Os
import common.applyDefaultSettings
import common.toCapitalized
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.Stage

class DocsTestProject(
    model: CIBuildModel,
    stage: Stage,
    os: Os,
    testJava: JvmCategory,
    configCacheEnabled: Boolean
) : Project({
    id("${model.projectId}_DocsTest_${testJava.version.name.toCapitalized()}_${os.asName()}")
    name = "Docs Test - ${testJava.version.name.toCapitalized()} ${os.asName()}"
}) {
    val docsTests: List<BaseGradleBuildType>

    init {
        val configCacheVariants = if (configCacheEnabled) listOf(true, false) else listOf(false)
        docsTests = configCacheVariants.flatMap {
            listOf(
                DocsTest(model, stage, os, testJava, 1, it, true, listOf("docsTest=org.gradle.docs.samples.Bucket1SnippetsTest")),
                DocsTest(model, stage, os, testJava, 2, it, true, listOf("docsTest=org.gradle.docs.samples.Bucket2SnippetsTest")),
                DocsTest(model, stage, os, testJava, 3, it, true, listOf("docsTest=org.gradle.docs.samples.Bucket3SnippetsTest")),
                DocsTest(
                    model, stage, os, testJava, 4, it, true,
                    listOf(
                        "docsTest=org.gradle.docs.samples.Bucket1SnippetsTest",
                        "docsTest=org.gradle.docs.samples.Bucket2SnippetsTest",
                        "docsTest=org.gradle.docs.samples.Bucket3SnippetsTest"
                    )
                )
            )
        }

        docsTests.forEach(this::buildType)
    }
}

class DocsTestTrigger(model: CIBuildModel, docsTestProject: DocsTestProject) : BaseGradleBuildType(init = {
    id("${docsTestProject.id}_Trigger")
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

class DocsTest(
    model: CIBuildModel,
    stage: Stage,
    os: Os,
    testJava: JvmCategory,
    index: Int,
    configCacheEnabled: Boolean,
    include: Boolean,
    testClasses: List<String>,
) : BaseGradleBuildType(stage = stage, init = {
    val docsTestName = "${if (configCacheEnabled) "ConfigCache" else ""}DocsTest"

    id("${model.projectId}_${docsTestName}_${testJava.version.name.toCapitalized()}_${os.asName()}_$index")
    name = "Docs Test${if (configCacheEnabled) " With Config Cache Enabled " else ""} - ${testJava.version.name.toCapitalized()} ${os.asName()} ($index)"

    features {
        publishBuildStatusToGithub(model)
    }

    applyTestDefaults(
        model,
        this,
        "docs:platformTest",
        timeout = 60,
        extraParameters = buildScanTag(docsTestName) +
            " -PenableConfigurationCacheForDocsTests=$configCacheEnabled" +
            " -PtestJavaVersion=${testJava.version.major}" +
            " -PtestJavaVendor=${testJava.vendor.name}" +
            if (include) " -PincludeTestClasses=true" else " -PexcludeTestClasses=true",
        preSteps = prepareTestClassesStep(os, include, testClasses)
    )
})

private fun prepareTestClassesStep(os: Os, include: Boolean, testClasses: List<String>): BuildSteps.() -> Unit {
    val action = if (include) "include" else "exclude"
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
