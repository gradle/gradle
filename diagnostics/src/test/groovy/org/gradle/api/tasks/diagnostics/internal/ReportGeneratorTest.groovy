/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal

import org.gradle.api.Project
import org.gradle.initialization.BuildClientMetaData
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class ReportGeneratorTest extends AbstractProjectBuilderSpec {

    ReportRenderer renderer = Mock(ReportRenderer)
    BuildClientMetaData buildClientMetaData = Mock(BuildClientMetaData)
    ReportGenerator.ReportAction<Project> projectReportGenerator = Mock(ReportGenerator.ReportAction)
    StyledTextOutput styledTextOutput = Mock(StyledTextOutput)

    def createReportGenerator(File file = null) {
        StyledTextOutputFactory textOutputFactory = Mock(StyledTextOutputFactory)
        textOutputFactory.create(_) >> styledTextOutput
        return new ReportGenerator(renderer, buildClientMetaData, file, textOutputFactory)
    }

    def 'completes renderer at end of generation'() {
        setup:
        def generator = createReportGenerator()

        when:
        generator.generateReport([project] as Set, projectReportGenerator)

        then:
        1 * renderer.setClientMetaData(buildClientMetaData)

        then:
        1 * renderer.setOutput(styledTextOutput)
        0 * renderer.setOutputFile(_)

        then:
        1 * renderer.startProject(projectDetails)

        then:
        1 * projectReportGenerator.execute(project)

        then:
        1 * renderer.completeProject(projectDetails)

        then:
        1 * renderer.complete()
    }

    def 'sets outputFileName on renderer before generation'() {
        setup:
        final File file = temporaryFolder.getTestDirectory().file("report.txt");
        def generator = createReportGenerator(file)

        when:
        generator.generateReport([project] as Set, projectReportGenerator)

        then:
        1 * renderer.setClientMetaData(buildClientMetaData)

        then:
        1 * renderer.setOutputFile(file)
        0 * renderer.setOutput(_)

        then:
        1 * renderer.startProject(projectDetails)

        then:
        1 * projectReportGenerator.execute(project)

        then:
        1 * renderer.completeProject(projectDetails)

        then:
        1 * renderer.complete()
    }


    def 'passes each project to renderer'() {
        setup:
        def child1 = TestUtil.createChildProject(project, "child1");
        def child2 = TestUtil.createChildProject(project, "child2");
        def generator = createReportGenerator()
        def child1Details = ProjectDetails.of(child1)
        def child2Details = ProjectDetails.of(child2)

        when:
        generator.generateReport(project.getAllprojects(), projectReportGenerator)

        then:
        1 * renderer.setClientMetaData(buildClientMetaData)
        1 * renderer.setOutput(styledTextOutput)

        then:
        1 * renderer.startProject(projectDetails)
        1 * projectReportGenerator.execute(project)
        1 * renderer.completeProject(projectDetails)

        then:
        1 * renderer.startProject(child1Details)
        1 * projectReportGenerator.execute(child1)
        1 * renderer.completeProject(child1Details)

        then:
        1 * renderer.startProject(child2Details)
        1 * projectReportGenerator.execute(child2)
        1 * renderer.completeProject(child2Details)

        then:
        1 * renderer.complete()
    }

    def getProjectDetails() {
        ProjectDetails.of(project)
    }
}
