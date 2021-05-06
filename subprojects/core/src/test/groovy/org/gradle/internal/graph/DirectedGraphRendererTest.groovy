/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.graph

import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TestStyledTextOutput
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class DirectedGraphRendererTest extends Specification {
    final DirectedGraph<String, Void> graph = Mock(DirectedGraph)
    final GraphNodeRenderer<String> nodeRenderer = Stub(GraphNodeRenderer) {
        renderTo(_, _) >> { String node, StyledTextOutput output -> output.text("[$node]")}
    }
    final DirectedGraphRenderer<String> renderer = new DirectedGraphRenderer<String>(nodeRenderer, graph)
    final TestStyledTextOutput output = new TestStyledTextOutput()

    def "renders a graph with a single root node"() {
        given:
        1 * graph.getNodeValues("1", _, _) >> { args -> }

        when:
        renderer.renderTo("1", output)

        then:
        output.value == """[1]
"""
    }

    def "renders a tree"() {
        given:
        1 * graph.getNodeValues("1", _, _) >> { args -> args[2] << "2"; args[2] << "3" }
        1 * graph.getNodeValues("2", _, _) >> { args -> args[2] << "4" }
        1 * graph.getNodeValues("3", _, _) >> { args -> }
        1 * graph.getNodeValues("4", _, _) >> { args -> args[2] << "5" }
        1 * graph.getNodeValues("5", _, _) >> { args -> }

        when:
        renderer.renderTo("1", output)

        then:
        output.value == """[1]
{info}+--- {normal}[2]
{info}|    \\--- {normal}[4]
{info}|         \\--- {normal}[5]
{info}\\--- {normal}[3]
"""
    }

    def "renders a graph that contains nodes with multiple incoming edges"() {
        given:
        1 * graph.getNodeValues("1", _, _) >> { args -> args[2] << "2"; args[2] << "3" }
        1 * graph.getNodeValues("2", _, _) >> { args -> args[2] << "4" }
        1 * graph.getNodeValues("3", _, _) >> { args -> args[2] << "2" }
        1 * graph.getNodeValues("4", _, _) >> { args -> }

        when:
        renderer.renderTo("1", output)

        then:
        output.value == """[1]
{info}+--- {normal}[2]
{info}|    \\--- {normal}[4]
{info}\\--- {normal}[3]
{info}     \\--- {normal}[2] (*)

{info}(*) - details omitted (listed previously){normal}
"""
    }

    def "renders a graph that contains cycles"() {
        given:
        1 * graph.getNodeValues("1", _, _) >> { args -> args[2] << "2"; args[2] << "3" }
        1 * graph.getNodeValues("2", _, _) >> { args -> args[2] << "3" }
        1 * graph.getNodeValues("3", _, _) >> { args -> args[2] << "2" }

        when:
        renderer.renderTo("1", output)

        then:
        output.value == """[1]
{info}+--- {normal}[2]
{info}|    \\--- {normal}[3]
{info}|         \\--- {normal}[2] (*)
{info}\\--- {normal}[3] (*)

{info}(*) - details omitted (listed previously){normal}
"""
    }

    def "renders a graph that contains a single node with an edge to itself"() {
        given:
        1 * graph.getNodeValues("1", _, _) >> { args -> args[2] << "1" }

        when:
        renderer.renderTo("1", output)

        then:
        output.value == """[1]
{info}\\--- {normal}[1] (*)

{info}(*) - details omitted (listed previously){normal}
"""
    }

    def "renders to an appendable"() {
        given:
        1 * graph.getNodeValues("1", _, _) >> { args -> args[2] << "2"; args[2] << "3" }
        1 * graph.getNodeValues("2", _, _) >> { args -> args[2] << "4" }
        1 * graph.getNodeValues("3", _, _) >> { args -> }
        1 * graph.getNodeValues("4", _, _) >> { args -> args[2] << "5" }
        1 * graph.getNodeValues("5", _, _) >> { args -> }

        when:
        StringWriter writer = new StringWriter()
        renderer.renderTo("1", writer)

        then:
        writer.toString() == TextUtil.toPlatformLineSeparators("""[1]
+--- [2]
|    \\--- [4]
|         \\--- [5]
\\--- [3]
""")
    }

}
