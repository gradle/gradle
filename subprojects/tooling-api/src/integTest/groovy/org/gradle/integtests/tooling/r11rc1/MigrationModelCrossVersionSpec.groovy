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
import org.gradle.tooling.model.internal.migration.ProjectOutput
import org.gradle.tooling.internal.migration.DefaultArchive
import org.gradle.tooling.internal.migration.DefaultTestResult

import org.junit.Rule

@MinToolingApiVersion("current")
@MinTargetGradleVersion("current")
class MigrationModelCrossVersionSpec extends ToolingApiSpecification {
    @Rule TestResources resources = new TestResources()

    def "modelContainsAllArchivesOnTheArchivesConfiguration"() {
        when:
        def output = withConnection { it.getModel(ProjectOutput.class) }

        then:
        output instanceof ProjectOutput
        def archives = output.taskOutputs.findAll { it.getClass().name == DefaultArchive.name } as List
        archives.size() == 2
        archives.any { it.file.name.endsWith(".jar") }
        archives.any { it.file.name.endsWith(".zip") }
    }

    def "modelContainsAllTestResults"() {
        when:
        def output = withConnection { it.getModel(ProjectOutput.class) }

        then:
        output instanceof ProjectOutput
        def testResults = output.taskOutputs.findAll { it.getClass().name == DefaultTestResult.name } as List
        testResults.size() == 2
        testResults.any { it.xmlReportDir == resources.dir.file("build", "test-results") }
        testResults.any { it.xmlReportDir == resources.dir.file("build", "other-results") }
    }

    def "modelContainsAllProjects"() {
        when:
        def output = withConnection { it.getModel(ProjectOutput.class) }

        then:
        output instanceof ProjectOutput
        output.children.size() == 2
        output.children.name as Set == ["project1", "project2"] as Set
        output.children[0].children.empty
        output.children[1].children.empty
    }
}