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
package org.gradle.api.tasks.diagnostics.internal

import org.gradle.internal.logging.text.StreamingStyledTextOutput
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.Matchers.containsLine

@CleanupTestDirectory
class TextReportRendererSpec extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());
    private final TextReportRenderer renderer = new TextReportRenderer();

    def "writes report to a file"() {
        when:
        File outFile = new File(temporaryFolder.getTestDirectory(), "report.txt");
        renderer.setOutputFile(outFile);

        then:
        renderer.textOutput instanceof StreamingStyledTextOutput

        when:
        renderer.complete();

        then:
        outFile.file
        renderer.textOutput == null
    }

    def "write root project header"() {
        given:
        def project = Mock(ProjectDetails)
        TestStyledTextOutput textOutput = new TestStyledTextOutput();

        when:
        renderer.output = textOutput
        renderer.startProject(project)
        renderer.completeProject(project)
        renderer.complete()

        then:
        containsLine(textOutput.toString(), "Root project 'test'")

        and:
        1 * project.displayName >> "root project 'test'"
        1 * project.description >> null
    }

    def "write subproject header"() {
        given:
        def subproject = Mock(ProjectDetails)
        TestStyledTextOutput textOutput = new TestStyledTextOutput();

        when:
        renderer.output = textOutput
        renderer.startProject(subproject)
        renderer.completeProject(subproject)
        renderer.complete()

        then:
        containsLine(textOutput.toString(), "Project ':subproject'")

        and:
        1 * subproject.description >> null
        1 * subproject.displayName >> "project ':subproject'"
    }

    def "includes project description in header"() {
        given:
        def project = Mock(ProjectDetails)
        TestStyledTextOutput textOutput = new TestStyledTextOutput()

        when:
        renderer.output = textOutput
        renderer.startProject(project)
        renderer.completeProject(project)
        renderer.complete()

        then:
        containsLine(textOutput.toString(), "Root project 'test' - this is the root project")

        and:
        1 * project.displayName >> "root project 'test'"
        1 * project.getDescription() >> "this is the root project"
    }
}
