/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal.dependencies

import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphsRenderer
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.SimpleDependency
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class AsciiDependencyReportRendererTest extends AbstractProjectBuilderSpec {
    private final TestStyledTextOutput textOutput = new TestStyledTextOutput().ignoreStyle()
    private final AsciiDependencyReportRenderer renderer = new AsciiDependencyReportRenderer()

    def setup() {
        renderer.output = textOutput
    }

    def "informs if no configurations"() {
        given:
        def projectDetails = ProjectDetails.of(project)

        when:
        renderer.startProject(projectDetails)
        renderer.completeProject(projectDetails)

        then:
        textOutput.value.contains('No configurations')
    }

    def "shows configuration header"() {
        given:
        def conf1 = new ConfigurationDetails('config1', 'description', true, null, null)
        def conf2 = new ConfigurationDetails('config2', null, false, null, null)

        when:
        renderer.prepareVisit()
        renderer.startConfiguration(conf1);
        renderer.completeConfiguration(conf1);
        renderer.startConfiguration(conf2);
        renderer.completeConfiguration(conf2);

        then:
        textOutput.value.readLines() == [
                'config1 - description',
                '',
                'config2 (n)'
        ]
    }

    def "renders dependency graph"() {
        renderer.dependencyGraphRenderer = Mock(DependencyGraphsRenderer)
        def root = new SimpleDependency("root")
        root.children.add(new SimpleDependency("dep"))

        when:
        renderer.renderNow(root)

        then:
        1 * renderer.dependencyGraphRenderer.render([root])
    }

    def "safely completes if no configurations"() {
        when:
        //no configuration started, and:
        renderer.complete()

        then:
        noExceptionThrown()
    }

    def "informs if no dependencies"() {
        def root = new SimpleDependency("root", true, "config")

        when:
        renderer.renderNow(root)

        then:
        textOutput.value.readLines() == ['No dependencies']
    }
}
