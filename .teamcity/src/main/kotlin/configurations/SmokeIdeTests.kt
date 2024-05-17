/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package configurations

import common.requiresNotEc2Agent
import model.CIBuildModel
import model.Stage

class SmokeIdeTests(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model))
    name = "Smoke Ide Tests"
    description = "Tests against IDE sync process"

    features {
        publishBuildStatusToGithub(model)
    }

    requirements {
        // These tests are usually heavy and the build time is twice on EC2 agents
        requiresNotEc2Agent()
    }

    applyTestDefaults(
        model = model,
        buildType = this,
        gradleTasks = ":smoke-ide-test:smokeIdeTest",
        extraParameters = buildScanTag("SmokeIdeTests"),
    )
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_SmokeIdeTests"
    }
}
