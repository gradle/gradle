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
package org.gradle.api.tasks.diagnostics;


import org.gradle.api.Project
import org.gradle.api.tasks.diagnostics.internal.AsciiDependencyReportRenderer
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.HelperUtil
import spock.lang.Specification

public class DependencyReportTaskTest extends Specification {

    private Project project = new ProjectBuilder().build()
    private DependencyReportTask task = HelperUtil.createTask(DependencyReportTask.class);
    private DependencyReportRenderer renderer = Mock(DependencyReportRenderer)

    void setup() {
        task.renderer = renderer
    }

    def "task is configured correctly"() {
        task = HelperUtil.createTask(DependencyReportTask.class);

        expect:
        task.renderer instanceof AsciiDependencyReportRenderer
        task.configurations == null
    }

    def "uses project configurations"() {
        given:
        def bConf = project.configurations.add("b-conf")
        def aConf = project.configurations.add("a-conf")

        when:
        task.generate(project)

        then: 1 * renderer.startConfiguration(aConf)
        then: 1 * renderer.render(aConf)
        then: 1 * renderer.completeConfiguration(aConf)


        then: 1 * renderer.startConfiguration(bConf)
        then: 1 * renderer.render(bConf)
        then: 1 * renderer.completeConfiguration(bConf)

        0 * renderer._
    }

    def "uses specific configurations"() {
        given:
        project.configurations.add("a")
        def bConf = project.configurations.add("b")
        task.configurations = [bConf] as Set

        when:
        task.generate(project)

        then:
        1 * renderer.startConfiguration(bConf)
        1 * renderer.render(bConf)
        1 * renderer.completeConfiguration(bConf)
        0 * renderer._
    }
}
