/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion('>=6.8')
class CompositeBuildBuildActionExecuterCrossVersionSpec extends ToolingApiSpecification {

    def "can run task from included build when running a build action"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            def buildAction = connection.action(new LoadCompositeModel(GradleProject))
            collectOutputs(buildAction)
            buildAction.forTasks([':other-build:sub:doSomething'])
            buildAction.run()
        }

        then:
        stdout.toString().contains("do something")
    }
}
