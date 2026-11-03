package configurations

import common.Os
import common.buildScanCustomValueParam
import common.buildScanTagParam
import common.getBuildScanCustomValueParam
import model.CIBuildModel
import model.Stage

/**
 * Runs property-based (unit) tests with a fresh seed and many more cases than regular CI,
 * which derandomizes them to keep builds reproducible.
 *
 * The seed is the TeamCity build id, which makes it simpler to reproduce failures locally.
 */
class PropertyBasedTests(
    model: CIBuildModel,
    stage: Stage,
) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
        id(buildTypeId(model))
        name = "Property-based test exploration"
        description = "Run property-based tests with a random seed and a large number of cases"

        val seed = "%teamcity.build.id%"
        applyDefaults(
            model,
            this,
            "test",
            timeout = 180,
            extraParameters =
                listOf(
                    stage.getBuildScanCustomValueParam(),
                    buildScanTagParam("PropertyBasedTests"),
                    buildScanCustomValueParam("propertyBasedTestSeed", seed),
                    "-PpropertyBasedTests=only",
                    "-PpropertyBasedTestSeed=$seed",
                    "-PpropertyBasedCaseCount=10000",
                ).joinToString(" "),
        )
    }) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_PropertyBasedTests"
    }
}
