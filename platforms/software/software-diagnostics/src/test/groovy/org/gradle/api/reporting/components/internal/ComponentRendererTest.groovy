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
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.diagnostics.internal.text.DefaultTextReportBuilder
import org.gradle.api.tasks.diagnostics.internal.text.TextReportBuilder
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.model.ModelMap
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.SourceComponentSpec
import org.gradle.platform.base.VariantComponentSpec
import spock.lang.Specification

class ComponentRendererTest extends Specification {
    def project = Stub(Project) {
        toString() >> "<project>"
    }
    def resolver = Stub(FileResolver)
    def output = new TestStyledTextOutput()
    def builder = new DefaultTextReportBuilder(output, resolver)
    def sourceSetRenderer = Mock(SourceSetRenderer)
    def binaryRenderer = Mock(BinaryRenderer)
    def renderer = new ComponentRenderer(sourceSetRenderer, binaryRenderer)

    def "renders component"() {
        def component = Stub(ComponentSpec)
        component.displayName >> "<component>"

        when:
        renderer.render(component, builder)

        then:
        output.value.contains("""{header}------------------------------------------------------------
<component>
------------------------------------------------------------{normal}
""")
    }

    def "renders component with no source sets"() {
        def component = Stub(SourceComponentSpec)

        when:
        renderer.render(component, builder)

        then:
        output.value.contains("No source sets")
    }

    def "renders component with no binaries"() {
        def component = Stub(VariantComponentSpec)
        component.binaries >> Mock(ModelMap) {
            values() >> []
        }

        when:
        renderer.render(component, builder)

        then:
        output.value.contains("No binaries")
    }

    def "renders component binaries ordered by name"() {
        def component = Stub(VariantComponentSpec)
        component.binaries >> Mock(ModelMap) {
            values() >> [binary("cBinary"), binary("aBinary"), binary("bBinary"), binary("dBinary")]
        }
        binaryRenderer.render(_, _) >> { BinarySpec binary, TextReportBuilder output -> output.output.println("binary: $binary.name") }

        when:
        renderer.render(component, builder)

        then:
        output.value.contains("""{header}Binaries
--------{normal}
binary: aBinary
binary: bBinary
binary: cBinary
binary: dBinary
""")
    }

    def binary(String name) {
        Mock(BinarySpec){
            _ * getName() >> name
        }
    }
}
