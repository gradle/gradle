/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components.internal

import org.gradle.api.Project
import org.gradle.logging.TestStyledTextOutput
import org.gradle.runtime.base.ProjectComponent
import spock.lang.Specification

class ComponentReportRendererTest extends Specification {
    def project = Stub(Project) {
        toString() >> "<project>"
    }
    def output = new TestStyledTextOutput()
    def renderer = new ComponentReportRenderer()

    def setup() {
        renderer.output = output
    }

    def "renders project with no components"() {
        when:
        renderer.startProject(project)
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("{info}No components{normal}")
    }

    def "renders project with single component"() {
        def component = Stub(ProjectComponent) {
            getDisplayName() >> "<component>"
        }

        when:
        renderer.startProject(project)
        renderer.startComponent(component)
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("\n{header}<component>\n")
    }

    def "renders project with multiple components"() {
        def component1 = Stub(ProjectComponent) {
            getDisplayName() >> "<component 1>"
        }
        def component2 = Stub(ProjectComponent) {
            getDisplayName() >> "<component 2>"
        }

        when:
        renderer.startProject(project)
        renderer.startComponent(component1)
        renderer.startComponent(component2)
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("\n{header}<component 1>\n")
        output.value.contains("\n{header}<component 2>\n")
    }

    def "renders component with no source sets"() {
        def component = Stub(ProjectComponent)

        when:
        renderer.startProject(project)
        renderer.startComponent(component)
        renderer.completeSourceSets()
        renderer.completeBinaries()
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("No source sets")
    }

    def "renders component with no binaries"() {
        def component = Stub(ProjectComponent)

        when:
        renderer.startProject(project)
        renderer.startComponent(component)
        renderer.completeSourceSets()
        renderer.completeBinaries()
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("No binaries")
    }

}
