/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException

@ToolingApiVersion('>=3.3')
@TargetGradleVersion('>=3.3')
class DisabledTaskExecutionOnIncludedBuildsCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        singleProjectBuildInSubfolder("includedBuild")
    }

    def "Tasks from GradleProject"() {
        setup:
        def projects = withConnection {
            action(new FetchIncludedGradleProjects()).run()
        }
        def includedTask = projects[0].tasks.find { it.name == 'tasks' }

        when:
        withBuild { BuildLauncher launcher ->
            launcher.forLaunchables(includedTask)
        }

        then:
        thrown(UnsupportedBuildArgumentException)
    }

    def "Tasks from BuildInvocations"() {
        setup:
        def invocations = withConnection {
            action(new FetchIncludedBuildInvocations()).run()
        }
        def includedTask = invocations[0].tasks[0]
        def includedSelector = invocations[0].taskSelectors[0]

        when:
        withBuild { BuildLauncher launcher ->
            launcher.forLaunchables(includedTask)
        }

        then:
        thrown(UnsupportedBuildArgumentException)

        when:
        withBuild { BuildLauncher launcher ->
            launcher.forLaunchables(includedSelector)
        }

        then:
        thrown(UnsupportedBuildArgumentException)
    }
}
