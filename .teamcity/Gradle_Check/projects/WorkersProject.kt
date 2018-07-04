package projects

import configurations.IndividualPerformanceScenarioWorkers
import jetbrains.buildServer.configs.kotlin.v2017_2.Project
import model.CIBuildModel

class WorkersProject(model: CIBuildModel) : Project({
    this.uuid = model.projectPrefix + "Workers"
    this.id = uuid
    this.name = "Workers"

    buildType(IndividualPerformanceScenarioWorkers(model))
})
