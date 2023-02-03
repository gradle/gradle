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

package org.gradle.plugins.ide.tooling.r54

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=5.4')
@TargetGradleVersion('>=5.4')
class ToolingApiEclipseModelAutoBuildTasksCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion('>=3.0 <5.4')
    def "returns false for old versions"() {
        setup:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)

        expect:
        !eclipseProject.hasAutoBuildTasks()
    }

    def "can query if Eclipse model contains tasks configured for auto-sync"() {
        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)

        then:
        !eclipseProject.hasAutoBuildTasks()

        when:
        buildFile << """
            plugins {
                id 'eclipse'
            }

            task foo { }

            eclipse {
                autoBuildTasks foo
            }
        """
        eclipseProject = loadToolingModel(EclipseProject)

        then:
        eclipseProject.hasAutoBuildTasks()
    }
}
