/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.render.internal.html

import org.gradle.api.Transformer
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonResult
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResult
import org.gradle.api.plugins.buildcomparison.gradle.internal.ComparableGradleBuildExecuter
import org.gradle.api.plugins.buildcomparison.gradle.internal.DefaultGradleBuildInvocationSpec
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.internal.DefaultBuildOutcomeAssociation
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcomeComparisonResult
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcomeComparisonResultHtmlRenderer
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcomeHtmlRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.BuildComparisonResultRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory
import org.gradle.api.plugins.buildcomparison.render.internal.DefaultBuildOutcomeRendererFactory
import org.gradle.util.TemporaryFolder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

class GradleBuildComparisonResultHtmlRendererTest extends Specification {

    @Rule TemporaryFolder dir = new TemporaryFolder()

    def comparisonRenderers = new DefaultBuildOutcomeComparisonResultRendererFactory(HtmlRenderContext)
    def outcomeRenderers = new DefaultBuildOutcomeRendererFactory(HtmlRenderContext)

    def unassociatedFrom = new HashSet<BuildOutcome>()
    def unassociatedTo = new HashSet<BuildOutcome>()
    def comparisons = new LinkedList<BuildOutcomeComparisonResult>()

    def writer = new StringWriter()

    def hostAttributes = [foo: "bar"]

    def sourceBuildDir = dir.createDir("source")
    def targetBuildDir = dir.createDir("target")
    def resolver = TestFiles.resolver(dir.dir)
    def sourceBuildSpec = new DefaultGradleBuildInvocationSpec(resolver, sourceBuildDir)
    def targetBuildSpec = new DefaultGradleBuildInvocationSpec(resolver, targetBuildDir)
    def sourceBuildExecuter = new ComparableGradleBuildExecuter(sourceBuildSpec)
    def targetBuildExecuter = new ComparableGradleBuildExecuter(targetBuildSpec)

    BuildComparisonResultRenderer makeRenderer() {
        new GradleBuildComparisonResultHtmlRenderer(comparisonRenderers, outcomeRenderers, Charset.defaultCharset(), sourceBuildExecuter, targetBuildExecuter, hostAttributes, new Transformer() {
            Object transform(Object original) {
                original.path
            }
        })
    }

    BuildComparisonResult makeResult(
            Set<BuildOutcome> unassociatedFrom = this.unassociatedFrom,
            Set<BuildOutcome> unassociatedTo = this.unassociatedTo,
            List<BuildOutcomeComparisonResult<?>> comparisons = this.comparisons
    ) {
        new BuildComparisonResult(unassociatedFrom, unassociatedTo, comparisons)
    }

    StringBuildOutcome str(name, value = name) {
        new StringBuildOutcome(name, value)
    }

    Set<StringBuildOutcome> strs(String... strings) {
        strings.collect { str(it) } as Set
    }

    BuildOutcomeComparisonResult strcmp(String from, String to) {
        new StringBuildOutcomeComparisonResult(
                new DefaultBuildOutcomeAssociation(str(from), str(to), StringBuildOutcome)
        )
    }

    Document render() {
        makeRenderer().render(makeResult(), writer)
        Jsoup.parse(writer.toString())
    }

    def "render some results"() {
        given:
        comparisonRenderers.registerRenderer(new StringBuildOutcomeComparisonResultHtmlRenderer())
        outcomeRenderers.registerRenderer(new StringBuildOutcomeHtmlRenderer())

        and:
        comparisons << strcmp("a", "a")
        comparisons << strcmp("a", "b")
        comparisons << strcmp("a", "c")

        unassociatedFrom << str("foo")
        unassociatedTo << str("bar")

        when:
        def html = render()

        then:
        // Just need to test that the renderers were called correctly, not the renderers themselves
        def tables = html.select(".build-outcome-comparison table")
        tables.size() == 3
        tables[0].select("th").text() == "Source Target Distance"
        tables[0].select("td")[0].text() == "a"
        tables[2].select("td")[2].text() == comparisons.last.distance.toString()
        html.select(".build-outcome.source").find { it.id() == "foo" }
        html.select(".build-outcome.target").find { it.id() == "bar" }
    }

}
