import common.JvmVendor
import common.JvmVersion
import common.Os
import common.VersionedSettingsBranch
import common.asBuildScanCustomValue
import common.getBuildScanCustomValueParam
import jetbrains.buildServer.configs.kotlin.DslContext
import model.CIBuildModel
import model.JsonBasedGradleSubprojectProvider
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class BuildScanTagUtilsTest {
    init {
        DslContext.initForTest()
    }

    private val subprojectProvider = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json"))
    private val model =
        CIBuildModel(
            projectId = "Check",
            branch = VersionedSettingsBranch.fromDslContext(),
            buildScanTags = listOf("Check"),
            subprojects = subprojectProvider,
        )

    @Test
    fun `test stage tags`() {
        assertEquals(
            "-DbuildScan.PartOf=QuickFeedbackLinuxOnly,QuickFeedback,PullRequestFeedback,ReadyforNightly,ReadyforRelease",
            model.stages[0].getBuildScanCustomValueParam(),
        )
        assertEquals(
            "-DbuildScan.PartOf=QuickFeedback,PullRequestFeedback,ReadyforNightly,ReadyforRelease",
            model.stages[1].getBuildScanCustomValueParam(),
        )
        assertEquals(
            "-DbuildScan.PartOf=ReadyforRelease",
            model.stages[4].getBuildScanCustomValueParam(),
        )
    }

    @Test
    fun `test functional test project tags`() {
        assertEquals(
            "QuickJava23AdoptiumLinuxAmd64",
            TestCoverage(1, TestType.QUICK, Os.LINUX, JvmVersion.JAVA_23, JvmVendor.OPENJDK).asBuildScanCustomValue(),
        )
    }
}
