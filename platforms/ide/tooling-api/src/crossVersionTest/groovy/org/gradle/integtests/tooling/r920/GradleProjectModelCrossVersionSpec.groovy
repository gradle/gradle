/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r920

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion(">=9.2.0")
@ToolingApiVersion(">=9.2.0")
class GradleProjectModelCrossVersionSpec extends ToolingApiSpecification {
    def "buildTreePath is available on GradleProject"() {
        includeProjects("a")
        settingsFile << '''
            rootProject.name = 'root'
            '''
        projectDir.file('a/build.gradle') << "// content not relevant"

        when:
        GradleProject model = loadToolingModel(GradleProject)

        then:
        model.findByPath(":a").buildTreePath == ":a"
        model.buildTreePath == ":"
    }
}
