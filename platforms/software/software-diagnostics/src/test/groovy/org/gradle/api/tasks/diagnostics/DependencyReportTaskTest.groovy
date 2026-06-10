/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class DependencyReportTaskTest extends AbstractProjectBuilderSpec {
    private DependencyReportTask task
    private DependencyReportRenderer renderer = Mock(DependencyReportRenderer)
    private Configuration conf1
    private Configuration conf2

    def setup() {
        task = TestUtil.createTask(DependencyReportTask.class, project)
        conf1 = project.configurations.create("conf1")
        conf2 = project.configurations.create("conf2")
        task.renderer = renderer
    }

    def "task is configured correctly"() {
        task = TestUtil.create(temporaryFolder).task(DependencyReportTask.class);

        expect:
        task.renderer.get() instanceof AsciiDependencyReportRenderer
        !task.configurations.isPresent()
    }

    def "renders all configurations in the project"() {
        when:
        def reportModel = task.calculateReportModelFor(project)

        then:
        reportModel.configurations.size() == 2
        reportModel.configurations[0].name == conf1.name
        reportModel.configurations[1].name == conf2.name
    }

    def "rendering can be limited to specific configurations"() {
        given:
        project.configurations.create("a")
        def bConf = project.configurations.create("b")
        task.configurations = [bConf] as Set

        when:
        def reportModel = task.calculateReportModelFor(project)

        then:
        reportModel.configurations.size() == 1
        reportModel.configurations[0].name == bConf.name
    }

    def "rendering can be limited to a single configuration, specified by name"() {
        given:
        project.configurations.create("a")
        def bConf = project.configurations.create("b")

        when:
        task.selectedConfiguration.set("b")

        then:
        task.configurations.get() == [bConf] as Set
    }

    def "configuration to render could be specified by camelCase shortcut"() {
        given:
        project.configurations.create("confAlpha")
        def confB = project.configurations.create("confBravo")

        when:
        task.selectedConfiguration.set("coB")

        then:
        task.configurations.get() == [confB] as Set
    }

    def "ambiguous configuration selection by camelCase shortcut fails"() {
        given:
        project.configurations.create("confAlpha")
        project.configurations.create("confAlfa")

        when:
        task.selectedConfiguration.set("coA")
        task.configurations.get()

        then:
        def exception = thrown(Exception)
        exception.cause instanceof InvalidUserDataException
    }

    def "rule-defined configuration should be found"() {
        given:
        project.afterEvaluate { p ->
            p.configurations.addRule("configuration defined by a rule", { s ->
                if (s == "confBravo") {
                    p.configurations.create("confBravo")
                }
            })
        }

        when:
        project.evaluate()
        task.selectedConfiguration = "confBravo"

        then:
        task.configurations.get() == [project.configurations.confBravo] as Set
    }
}
