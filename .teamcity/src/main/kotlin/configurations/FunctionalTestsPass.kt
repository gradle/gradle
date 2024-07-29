/*
 * Copyright 2019 the original author or authors.
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

import common.applyDefaultSettings
import model.CIBuildModel
import model.TestCoverage
import projects.FunctionalTestProject

class FunctionalTestsPass(model: CIBuildModel, functionalTestProject: FunctionalTestProject) :
    OsAwareBaseGradleBuildType(os = functionalTestProject.testCoverage.os, init = {
        id("${functionalTestProject.testCoverage.asId(model)}_Trigger")
        name = functionalTestProject.name + " (Trigger)"
        type = Type.COMPOSITE

        applyDefaultSettings()

        features {
            publishBuildStatusToGithub(model)
        }

        dependencies {
            snapshotDependencies(functionalTestProject.functionalTests)
        }
    }) {
    val testCoverage: TestCoverage = functionalTestProject.testCoverage
}
