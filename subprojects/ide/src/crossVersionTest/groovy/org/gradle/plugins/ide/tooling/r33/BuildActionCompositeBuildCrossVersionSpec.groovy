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

package org.gradle.plugins.ide.tooling.r33

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

@ToolingApiVersion('>=3.3')
@TargetGradleVersion('>=3.3')
class BuildActionCompositeBuildCrossVersionSpec extends ToolingApiSpecification {

    def "Can fetch build scoped models from included builds"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        when:
        def environments = withConnection {
            action(new FetchBuildEnvironments()).run()
        }

        then:
        environments.size() == 2
    }

    def "Can fetch project scoped models from included builds"() {
        given:
        multiProjectBuildInRootFolder("root", ["a", "b"]) {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        multiProjectBuildInSubFolder("includedBuild", ["c", "d"])

        when:
        def eclipseProjects = withConnection {
            action(new FetchEclipseProjects()).run()
        }

        then:
        eclipseProjects*.name == ['root', 'a', 'b', 'includedBuild', 'c', 'd']
    }
}
