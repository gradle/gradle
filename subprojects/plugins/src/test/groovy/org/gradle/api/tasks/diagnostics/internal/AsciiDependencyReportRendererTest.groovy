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
package org.gradle.api.tasks.diagnostics.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.tasks.diagnostics.internal.dependencies.SimpleDependency
import org.gradle.logging.TestStyledTextOutput
import org.gradle.util.HelperUtil
import spock.lang.Specification

class AsciiDependencyReportRendererTest extends Specification {
    private final TestStyledTextOutput textOutput = new TestStyledTextOutput().ignoreStyle()
    private final AsciiDependencyReportRenderer renderer = new AsciiDependencyReportRenderer()
    private final Project project = HelperUtil.createRootProject()

    def setup() {
        renderer.output = textOutput
    }

    def writesMessageWhenProjectHasNoConfigurations() {
        when:
        renderer.startProject(project);
        renderer.completeProject(project);

        then:
        textOutput.value.contains('No configurations')
    }

    def writesConfigurationHeader() {
        Configuration configuration1 = Mock()
        configuration1.getName() >> 'config1'
        configuration1.getDescription() >> 'description'
        Configuration configuration2 = Mock()
        configuration2.getName() >> 'config2'

        when:
        renderer.startConfiguration(configuration1);
        renderer.completeConfiguration(configuration1);
        renderer.startConfiguration(configuration2);
        renderer.completeConfiguration(configuration2);

        then:
        textOutput.value.readLines() == [
                'config1 - description',
                '',
                'config2'
        ]
    }

    //TODO SF move this test elsewhere
    def rendersDependencyTreeForConfiguration() {
        ConfigurationInternal configuration = Mock()
        configuration.name >> 'config'

        def root = new SimpleDependency("root")
        def dep1 = new SimpleDependency("dep1")
        def dep11 = new SimpleDependency("dep1.1")
        def dep2 = new SimpleDependency("dep2")
        def dep21 = new SimpleDependency("dep2.1")
        def dep22 = new SimpleDependency("dep2.2")

        root.children.addAll(dep1, dep2)
        dep1.children.addAll(dep11)
        dep2.children.addAll(dep21, dep22)

        when:
        renderer.startConfiguration(configuration)
        renderer.renderNow(root)

        then:
        textOutput.value.readLines() == [
                'config',
                '+--- dep1',
                '|    \\--- dep1.1',
                '\\--- dep2',
                '     +--- dep2.1',
                '     \\--- dep2.2'
        ]
    }

    def rendersDependencyTreeForEmptyConfiguration() {
        def root = new SimpleDependency("root", "config")

        when:
        renderer.renderNow(root)

        then:
        textOutput.value.readLines() == ['No dependencies']
    }
}
