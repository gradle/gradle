package projects_g4

import configurations_g4.IndividualPerformanceScenarioWorkers
import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_1.Project
import model_g4.CIBuildModel

class WorkersProject(model: CIBuildModel) : Project({
    this.uuid = model.projectPrefix + "Workers"
    this.id = AbsoluteId(uuid)
    this.name = "Workers"

    buildType(IndividualPerformanceScenarioWorkers(model))
})
