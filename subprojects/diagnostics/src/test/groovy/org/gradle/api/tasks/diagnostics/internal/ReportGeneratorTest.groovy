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

import org.gradle.initialization.BuildClientMetaData
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class ReportGeneratorTest extends Specification {

    ReportRenderer renderer = Mock(ReportRenderer)
    BuildClientMetaData buildClientMetaData = Mock(BuildClientMetaData)
    ProjectReportGenerator projectReportGenerator = Mock(ProjectReportGenerator)
    StyledTextOutput styledTextOutput = Mock(StyledTextOutput)

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    def createReportGenerator(File file = null) {
        StyledTextOutputFactory textOutputFactory = Mock(StyledTextOutputFactory)
        textOutputFactory.create(_) >> styledTextOutput
        return new ReportGenerator(renderer, buildClientMetaData, file, textOutputFactory, projectReportGenerator)
    }

    def 'completes renderer at end of generation'() {
        setup:
        def generator = createReportGenerator()
        def project = TestUtil.createRootProject()

        when:
        generator.generateReport([project] as Set)

        then:
        1 * renderer.setClientMetaData(buildClientMetaData)

        then:
        1 * renderer.setOutput(styledTextOutput)
        0 * renderer.setOutputFile(_)

        then:
        1 * renderer.startProject(project)

        then:
        1 * projectReportGenerator.generateReport(project)

        then:
        1 * renderer.completeProject(project)

        then:
        1 * renderer.complete()
    }

    def 'sets outputFileName on renderer before generation'() {
        setup:
        final File file = tmpDir.getTestDirectory().file("report.txt");
        def generator = createReportGenerator(file)
        def project = TestUtil.createRootProject()

        when:
        generator.generateReport([project] as Set)

        then:
        1 * renderer.setClientMetaData(buildClientMetaData)

        then:
        1 * renderer.setOutputFile(file)
        0 * renderer.setOutput(_)

        then:
        1 * renderer.startProject(project)

        then:
        1 * projectReportGenerator.generateReport(project)

        then:
        1 * renderer.completeProject(project)

        then:
        1 * renderer.complete()
    }


    def 'passes each project to renderer'() {
        setup:
        def project = TestUtil.createRootProject()
        def child1 = TestUtil.createChildProject(project, "child1");
        def child2 = TestUtil.createChildProject(project, "child2");
        def generator = createReportGenerator()

        when:
        generator.generateReport(project.getAllprojects())

        then:
        1 * renderer.setClientMetaData(buildClientMetaData)
        1 * renderer.setOutput(styledTextOutput)

        then:
        1 * renderer.startProject(project)
        1 * projectReportGenerator.generateReport(project)
        1 * renderer.completeProject(project)

        then:
        1 * renderer.startProject(child1)
        1 * projectReportGenerator.generateReport(child1)
        1 * renderer.completeProject(child1)

        then:
        1 * renderer.startProject(child2)
        1 * projectReportGenerator.generateReport(child2)
        1 * renderer.completeProject(child2)

        then:
        1 * renderer.complete()
    }
}
