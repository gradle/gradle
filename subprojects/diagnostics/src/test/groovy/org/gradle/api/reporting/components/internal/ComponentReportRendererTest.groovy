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


import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.language.base.LanguageSourceSet
import org.gradle.model.ModelMap
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.SourceComponentSpec
import spock.lang.Specification

class ComponentReportRendererTest extends Specification {
    def project = Stub(ProjectDetails)
    def resolver = Stub(FileResolver)
    def binaryRenderer = Stub(TypeAwareBinaryRenderer)
    def output = new TestStyledTextOutput()
    def renderer = new ComponentReportRenderer(resolver, binaryRenderer)

    def setup() {
        renderer.output = output
    }

    def "renders project with no components"() {
        when:
        renderer.startProject(project)
        renderer.renderComponents([])
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("{info}No components defined for this project.{normal}")
    }

    def "renders project with single component"() {
        def component = Stub(ComponentSpec) {
            getDisplayName() >> "<component>"
        }

        when:
        renderer.startProject(project)
        renderer.renderComponents([component])
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("\n{header}<component>\n")
    }

    def "renders project with multiple components"() {
        def component1 = Stub(ComponentSpec) {
            getDisplayName() >> "<component 1>"
        }
        def component2 = Stub(ComponentSpec) {
            getDisplayName() >> "<component 2>"
        }

        when:
        renderer.startProject(project)
        renderer.renderComponents([component1, component2])
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("""
{header}<component 1>
""")
        output.value.contains("""
{header}<component 2>
""")
    }

    def "renders additional source sets"() {
        def sourceSet1 = Stub(LanguageSourceSet)
        def sourceSet2 = Stub(LanguageSourceSet) {
            getDisplayName() >> "<source set>"
        }
        def component = Stub(SourceComponentSpec) {
            getSources() >> Stub(ModelMap) {
                values() >> [sourceSet1]
            }
        }

        when:
        renderer.startProject(project)
        renderer.renderComponents([component])
        renderer.renderSourceSets([sourceSet1, sourceSet2])
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("""{header}Additional source sets
----------------------{normal}
<source set>
    No source directories

""")
    }

    def "renders additional binaries"() {
        def binary1 = Stub(BinarySpec)
        def binary2 = Stub(BinarySpec)
        def component = Stub(ComponentSpec) {
            getBinaries() >> Stub(ModelMap) {
                values() >> [binary1]
            }
        }
        binaryRenderer.render(binary2, _) >> { BinarySpec binary, TextReportBuilder builder -> builder.output.println("<binary>") }

        when:
        renderer.startProject(project)
        renderer.renderComponents([component])
        renderer.renderBinaries([binary2])
        renderer.completeProject(project)
        renderer.complete()

        then:
        output.value.contains("""{header}Additional binaries
-------------------{normal}
<binary>
""")
    }
}
