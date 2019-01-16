package projects

import configurations.IndividualPerformanceScenarioWorkers
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel

class WorkersProject(model: CIBuildModel) : Project({
    this.uuid = model.projectPrefix + "Workers"
    this.id = AbsoluteId(uuid)
    this.name = "Workers"

    buildType(IndividualPerformanceScenarioWorkers(model))
})
