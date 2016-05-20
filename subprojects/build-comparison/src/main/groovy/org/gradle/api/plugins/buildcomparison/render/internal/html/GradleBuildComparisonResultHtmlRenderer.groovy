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

import groovy.xml.MarkupBuilder
import org.apache.commons.lang.StringEscapeUtils
import org.gradle.api.Transformer
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonResult
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResult
import org.gradle.api.plugins.buildcomparison.gradle.internal.ComparableGradleBuildExecuter
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome
import org.gradle.api.plugins.buildcomparison.render.internal.*

import java.nio.charset.Charset

class GradleBuildComparisonResultHtmlRenderer implements BuildComparisonResultRenderer<Writer> {

    private final BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> comparisonRenderers
    private final BuildOutcomeRendererFactory outcomeRenderers

    private final Transformer<String, File> filePathRelativizer
    private final ComparableGradleBuildExecuter sourceExecuter
    private final ComparableGradleBuildExecuter targetExecuter
    private final Map<String, String> hostAttributes
    private final Charset encoding

    GradleBuildComparisonResultHtmlRenderer(
            BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> comparisonRenderers,
            BuildOutcomeRendererFactory outcomeRenderers,
            Charset encoding,
            ComparableGradleBuildExecuter sourceExecuter,
            ComparableGradleBuildExecuter targetExecuter,
            Map<String, String> hostAttributes,
            Transformer<String, File> fileRelativizer
    ) {
        this.comparisonRenderers = comparisonRenderers
        this.outcomeRenderers = outcomeRenderers
        this.encoding = encoding
        this.sourceExecuter = sourceExecuter
        this.targetExecuter = targetExecuter
        this.hostAttributes = hostAttributes
        this.filePathRelativizer = fileRelativizer
    }

    void render(BuildComparisonResult result, Writer writer) {
        MarkupBuilder markupBuilder = new MarkupBuilder(new IndentPrinter(writer))
        HtmlRenderContext context = new HtmlRenderContext(markupBuilder, filePathRelativizer)

        markupBuilder.html {
            head {
                renderHead(result, context)
            }
            body {
                div("class": "text-container") {
                    renderHeading(result, context)
                    renderUncomparedOutcomes(result, context)
                    renderOutcomeComparisons(result, context)
                }
            }
        }
    }

    private void renderUncomparedOutcomes(BuildComparisonResult result, HtmlRenderContext context) {
        renderUncomparedOutcomeSet(true, result.uncomparedSourceOutcomes, context)
        renderUncomparedOutcomeSet(false, result.uncomparedTargetOutcomes, context)
    }

    protected void renderUncomparedOutcomeSet(boolean isSource, Set<BuildOutcome> uncompareds, HtmlRenderContext context) {
        def side = isSource ? "source" : "target"
        def other = isSource ? "target" : "source"

        if (uncompareds) {
            context.render {
                h2 "Uncompared ${side} outcomes"
                p "Uncompared ${side} build outcomes are outcomes that were not matched with a ${other} build outcome."

                def sortedUncompareds = uncompareds.sort { it.name }
                ol {
                    for (uncompared in sortedUncompareds) {
                        li {
                            a("class": context.diffClass(false), href: "#${uncompared.name}", uncompared.name)
                        }
                    }
                }

                for (uncompared in sortedUncompareds) {
                    BuildOutcomeRenderer renderer = outcomeRenderers.getRenderer(uncompared.getClass())

                    if (renderer == null) {
                        throw new IllegalArgumentException(String.format("Cannot find renderer for build outcome type: %s", uncompared.getClass()))
                    }

                    div("class": "build-outcome text-container ${side}", id: uncompared.name) {
                        renderer.render(uncompared, context)
                    }
                }
            }
        }
    }

    protected void renderOutcomeComparisons(BuildComparisonResult result, HtmlRenderContext context) {
        context.render {
            h2 "Compared build outcomes"
            p "Compared build outcomes are outcomes that have been identified as being intended to be the same between the target and source build."

            def comparisons = result.comparisons.sort { name it }
            ol {
                for (comparison in comparisons) {
                    if (!comparison.outcomesAreIdentical) {
                        li {
                            // TODO: assuming that the names are unique and that they are always the same on both sides which they are in 1.2
                            a("class": context.diffClass(comparison.outcomesAreIdentical), href: "#${name(comparison)}", name(comparison))
                        }
                    }
                }
                for (comparison in comparisons) {
                    if (comparison.outcomesAreIdentical) {
                        li {
                            // TODO: assuming that the names are unique and that they are always the same on both sides which they are in 1.2
                            a("class": context.diffClass(true), href: "#${name(comparison)}", name(comparison))
                        }
                    }
                }
            }

            for (BuildOutcomeComparisonResult comparison in comparisons) {
                if (!comparison.outcomesAreIdentical) {
                    BuildOutcomeComparisonResultRenderer renderer = comparisonRenderers.getRenderer(comparison.getClass())

                    if (renderer == null) {
                        throw new IllegalArgumentException(String.format("Cannot find renderer for build outcome comparison result type: %s", comparison.getClass()))
                    }

                    div("class": "build-outcome-comparison text-container", id: name(comparison)) {
                        renderer.render(comparison, context)
                    }
                }
            }
            for (BuildOutcomeComparisonResult comparison in comparisons) {
                if (comparison.outcomesAreIdentical) {
                    BuildOutcomeComparisonResultRenderer renderer = comparisonRenderers.getRenderer(comparison.getClass())

                    if (renderer == null) {
                        throw new IllegalArgumentException(String.format("Cannot find renderer for build outcome comparison result type: %s", comparison.getClass()))
                    }

                    div("class": "build-outcome-comparison text-container", id: name(comparison)) {
                        renderer.render(comparison, context)
                    }
                }
            }
        }
    }

    void renderHead(BuildComparisonResult result, HtmlRenderContext context) {
        context.render {
            title "Gradle Build Comparison"
            meta("http-equiv": "Content-Type", content: "text/html; charset=$encoding")
            style {
                mkp.yieldUnescaped loadBaseCss()

                mkp.yieldUnescaped """
                    .${context.diffClass} { color: red !important; }
                    .${context.equalClass} { color: green !important; }

                    .container {
                        padding: 30px;
                        background-color: #FFF7F7;
                        border-radius: 10px;
                        border: 1px solid #E0E0E0;
                    }

                    .build-outcome-comparison, .build-outcome {
                        padding: 10px 10px 14px 20px;
                        border: 1px solid #D0D0D0;
                        border-radius: 6px;
                        margin-bottom: 1.2em;
                    }

                    .build-outcome-comparison > :last-child, .build-outcome > :last-child {
                        margin-bottom: 0;
                    }

                    .build-outcome-comparison > :first-child, .build-outcome > :first-child {
                         margin-top: 0;
                    }

                    .warning {
                        background-color: #FFEFF2;
                        color: red;
                        border: 1px solid #FFCBCB;
                        padding: 6px;
                        border-radius: 6px;
                        display: inline-block;
                    }

                    .warning > :last-child {
                        margin-bottom: 0;
                    }
                """
            }
        }
    }

    void renderHeading(BuildComparisonResult result, HtmlRenderContext context) {
        context.render {
            h1 "Gradle Build Comparison"

            p(id: "overall-result", "class": context.equalOrDiffClass(result.buildsAreIdentical)) {
                b result.buildsAreIdentical ?
                    "The build outcomes were found to be identical." :
                    "The build outcomes were not found to be identical."
            }

            div(id: "host-details") {
                h2 "Comparison host details"
                def lines = hostAttributes.collect {
                    "<strong>${StringEscapeUtils.escapeHtml(it.key)}</strong>: ${StringEscapeUtils.escapeHtml(it.value) }"
                }
                p {
                    mkp.yieldUnescaped lines.join("<br />")
                }
            }

            def sourceBuild = sourceExecuter.spec
            def targetBuild = targetExecuter.spec

            div(id: "compared-builds") {
                h2 "Compared builds"

                table {
                    tr {
                        th class: "border-right", ""
                        th "Source Build"
                        th "Target Build"
                    }

                    def sourceBuildPath = sourceBuild.projectDir.absolutePath
                    def targetBuildPath = targetBuild.projectDir.absolutePath
                    tr("class": context.diffClass(sourceBuildPath == targetBuildPath)) {
                        th class: "border-right no-border-bottom", "Project"
                        td sourceBuildPath
                        td targetBuildPath
                    }

                    def sourceGradleVersion = sourceBuild.gradleVersion
                    def targetGradleVersion = targetBuild.gradleVersion
                    tr("class": context.diffClass(sourceGradleVersion == targetGradleVersion)) {
                        th class: "border-right no-border-bottom", "Gradle version"
                        td sourceGradleVersion
                        td targetGradleVersion
                    }

                    def sourceBuildTasks = sourceBuild.tasks
                    def targetBuildTasks = targetBuild.tasks
                    tr("class": context.diffClass(sourceBuildTasks == targetBuildTasks)) {
                        th class: "border-right no-border-bottom", "Tasks"
                        td sourceBuildTasks.join(" ")
                        td targetBuildTasks.join(" ")
                    }

                    def sourceBuildArguments = sourceBuild.arguments
                    def targetBuildArguments = targetBuild.arguments
                    tr("class": context.diffClass(sourceBuildArguments == targetBuildArguments)) {
                        th class: "border-right no-border-bottom", "Arguments"
                        td sourceBuildArguments.join(" ")
                        td targetBuildArguments.join(" ")
                    }
                }
            }
        }
    }

    String name(BuildOutcomeComparisonResult<? extends BuildOutcome> comparisonResult) {
        comparisonResult.compared.source.name
    }

    private String loadBaseCss() {
        URL resource = getClass().getResource("base.css")
        if (resource) {
            resource.getText("utf8")
        } else {
            /*
                The file is generated during the build and added to the classpath by the processResources task
                I don't want to make this fatal as it could break developer test runs in the IDE.
                TODO - add a distribution test to make sure the file is there
            */

            " /* base css not found at runtime */ "
        }
    }

}
