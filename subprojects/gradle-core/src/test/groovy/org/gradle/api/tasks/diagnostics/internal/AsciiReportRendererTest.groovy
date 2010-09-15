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
import org.gradle.logging.internal.TestStyledTextOutput
import org.gradle.util.HelperUtil
import spock.lang.Specification
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency

class AsciiReportRendererTest extends Specification {
    private final TestStyledTextOutput textOutput = new TestStyledTextOutput()
    private final AsciiReportRenderer renderer = new AsciiReportRenderer()
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
        _ * configuration1.getName() >> 'config1'
        _ * configuration1.getDescription() >> 'description'
        Configuration configuration2 = Mock()
        _ * configuration2.getName() >> 'config2'

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

    def rendersDependencyTreeForConfiguration() {
        ResolvedConfiguration resolvedConfig = Mock()
        Configuration configuration = Mock()
        ResolvedDependency dep1 = Mock()
        ResolvedDependency dep11 = Mock()
        ResolvedDependency dep2 = Mock()
        ResolvedDependency dep21 = Mock()
        ResolvedDependency dep22 = Mock()
        _ * configuration.name >> 'config'
        _ * resolvedConfig.getFirstLevelModuleDependencies() >> {[dep1, dep2] as LinkedHashSet}
        _ * dep1.getChildren() >> {[dep11] as LinkedHashSet}
        _ * dep1.getName() >> 'dep1'
        _ * dep1.getConfiguration() >> 'config1'
        _ * dep11.getChildren() >> {[] as LinkedHashSet}
        _ * dep11.getName() >> 'dep1.1'
        _ * dep11.getConfiguration() >> 'config1.1'
        _ * dep2.getChildren() >> {[dep21, dep22] as LinkedHashSet}
        _ * dep2.getName() >> 'dep2'
        _ * dep2.getConfiguration() >> 'config2'
        _ * dep21.getChildren() >> {[] as LinkedHashSet}
        _ * dep21.getName() >> 'dep2.1'
        _ * dep21.getConfiguration() >> 'config2.1'
        _ * dep22.getChildren() >> {[] as LinkedHashSet}
        _ * dep22.getName() >> 'dep2.2'
        _ * dep22.getConfiguration() >> 'config2.2'

        when:
        renderer.startConfiguration(configuration)
        renderer.render(resolvedConfig)

        then:
        textOutput.value.readLines() == [
                'config',
                '+--- dep1 [config1]',
                '|    \\--- dep1.1 [config1.1]',
                '\\--- dep2 [config2]',
                '     +--- dep2.1 [config2.1]',
                '     \\--- dep2.2 [config2.2]'
        ]
    }

    def rendersDependencyTreeForEmptyConfiguration() {
        ResolvedConfiguration configuration = Mock()
        _ * configuration.getFirstLevelModuleDependencies() >> {[] as Set}

        when:
        renderer.render(configuration)

        then:
        textOutput.value.readLines() == ['No dependencies']
    }
}
