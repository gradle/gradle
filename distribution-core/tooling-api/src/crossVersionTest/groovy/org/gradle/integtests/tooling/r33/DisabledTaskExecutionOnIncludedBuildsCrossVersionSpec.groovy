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
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.model.gradle.BuildInvocations

@ToolingApiVersion('>=3.3')
@TargetGradleVersion('>=3.3')
class DisabledTaskExecutionOnIncludedBuildsCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion('>=3.3 <6.8')
    def "Can't launch tasks from included builds via launchables obtained from GradleProject model"() {
        setup:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        def projects = withConnection {
            action(new FetchIncludedGradleProjects()).run()
        }
        def includedTask = projects[0].tasks.find { it.name == 'tasks' }

        when:
        withBuild { BuildLauncher launcher ->
            launcher.forLaunchables(includedTask)
        }

        then:
        thrown(BuildException)
    }

    @TargetGradleVersion('>=3.3 <6.8')
    def "Can't launch tasks from included builds via launchables obtained from BuildInvocations model"() {
        setup:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        def invocations = withConnection {
            action(new FetchIncludedBuildInvocations()).run()
        }
        def includedTask = invocations[0].tasks[0]
        def includedSelector = invocations[0].taskSelectors[0]

        when:
        withBuild { BuildLauncher launcher ->
            launcher.forLaunchables(includedSelector)
        }

        then:
        thrown(BuildException)

        when:
        withBuild { BuildLauncher launcher ->
            launcher.forLaunchables(includedTask)
        }

        then:
        thrown(BuildException)
    }

    def "Still can launch tasks from non-included subprojects"() {
        setup:
        multiProjectBuildInRootFolder("root", ['sub1'])
        settingsFile << """
            includeBuild 'includedBuild'
        """
        singleProjectBuildInSubfolder("includedBuild")

        when:
        withConnection(toolingApi.connector().forProjectDirectory(projectDir.file('sub1')).searchUpwards(true)) {
            BuildInvocations invocations = getModel(BuildInvocations)
            def task = invocations.tasks.find { it.name.contains 'tasks' }
            def selector = invocations.taskSelectors.find { it.name.contains 'tasks' }
            newBuild().forLaunchables(task).run()
            newBuild().forLaunchables(selector).run()
        }

        then:
        notThrown(Throwable)
    }
}
