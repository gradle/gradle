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

package org.gradle.integtests.tooling.r55

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject

@TargetGradleVersion(">=4.0")
class CompositeDeduplicationCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile << """
        project(':b') {
            apply plugin: 'eclipse'
            eclipse {
                project.name='explicitName'
            }
        }
        """
        settingsFile << """
        rootProject.name = 'root'
        include ':a', ':b'
        """
    }

    def "Included builds are deduplicated"() {
        given:
        settingsFile << """
                includeBuild 'includedBuild1'
                includeBuild 'includedBuild2'
            """
        multiProjectBuildInSubFolder("includedBuild1", ["a", "b", "c"])
        multiProjectBuildInSubFolder("includedBuild2", ["a", "b", "c"])

        when:
        def eclipseModels = withConnection { con -> con.action(new LoadCompositeEclipseModels()).run() }

        then:
        eclipseModels.collect {
            collectProjects(it)
        }.flatten().collect {
            it.name
        }.containsAll([
            'root', 'root-a', 'explicitName',
            'includedBuild1', 'includedBuild1-a', 'includedBuild1-b', 'includedBuild1-c',
            'includedBuild2', 'includedBuild2-a', 'includedBuild2-b', 'includedBuild2-c'])
    }

    Collection<EclipseProject> collectProjects(EclipseProject parent) {
        return parent.children.collect { collectProjects(it) }.flatten() + [parent]
    }

}
