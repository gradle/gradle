package projects

import configurations.IndividualPerformanceScenarioWorkers
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel

class WorkersProject(model: CIBuildModel) : Project({
    this.uuid = model.projectPrefix + "Workers"
    this.extId = uuid
    this.name = "Workers"

    buildType(IndividualPerformanceScenarioWorkers(model))
})
