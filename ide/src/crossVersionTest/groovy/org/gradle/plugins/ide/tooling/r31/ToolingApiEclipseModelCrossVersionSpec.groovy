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

package org.gradle.plugins.ide.tooling.r31

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject

@TargetGradleVersion('>=3.1')
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    def "Eclipse model can contain GStrings"() {
        given:
        buildFile << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    name = "\${'root'}"
                    buildCommand 'buildCommandWithArguments', argumentKey: "\${'argumentValue'}"
                }
            }
        """
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def buildCommands = rootProject.buildCommands

        then:
        rootProject.name == 'root'
        buildCommands.size() == 1
        buildCommands[0].name == 'buildCommandWithArguments'
        buildCommands[0].arguments.size() == 1
        buildCommands[0].arguments['argumentKey'] == 'argumentValue'
    }

}
