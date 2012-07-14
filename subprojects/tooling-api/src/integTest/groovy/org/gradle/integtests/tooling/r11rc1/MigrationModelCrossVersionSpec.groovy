/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.r11rc1

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.fixtures.TestResources
import org.gradle.tooling.model.migration.ProjectOutput
import org.gradle.tooling.model.migration.Archive

import org.junit.Rule
import spock.lang.Ignore

@MinToolingApiVersion("current")
@MinTargetGradleVersion("current")
@Ignore
class MigrationModelCrossVersionSpec extends ToolingApiSpecification {
    @Rule TestResources resources = new TestResources()

    def "canGetProjectOutputModel"() {
        given:

        when:
        def output = withConnection { it.getModel(ProjectOutput.class) }

        then:
        output instanceof ProjectOutput
        output.taskOutputs.size() == 1
        def taskOutput = output.taskOutputs.iterator().next()
        taskOutput instanceof Archive
        taskOutput.file.toString().endsWith(".jar")
    }
}