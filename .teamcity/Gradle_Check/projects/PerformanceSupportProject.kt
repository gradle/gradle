package projects

import configurations.IndividualPerformanceScenarioWorkers
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel

class PerformanceSupportProject(model: CIBuildModel) : Project({
    this.uuid = model.projectPrefix + "SupportPerformance"
    this.extId = uuid
    this.name = "Support Performance"

    buildType(IndividualPerformanceScenarioWorkers(model))
})
