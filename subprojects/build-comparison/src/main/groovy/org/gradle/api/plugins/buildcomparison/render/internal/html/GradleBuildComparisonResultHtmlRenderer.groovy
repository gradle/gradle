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

import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResult

import org.gradle.api.plugins.buildcomparison.render.internal.BuildComparisonResultRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.BuildOutcomeComparisonResultRendererFactory
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonResult
import org.gradle.api.plugins.buildcomparison.render.internal.BuildOutcomeComparisonResultRenderer
import org.gradle.api.Transformer
import org.gradle.util.GradleVersion
import org.apache.commons.lang.StringEscapeUtils
import org.gradle.api.plugins.buildcomparison.gradle.internal.ComparableGradleBuildExecuter
import java.nio.charset.Charset

class GradleBuildComparisonResultHtmlRenderer implements BuildComparisonResultRenderer<Writer> {

    private final BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers

    Transformer<String, File> filePathRelativizer
    ComparableGradleBuildExecuter sourceExecuter
    ComparableGradleBuildExecuter targetExecuter
    Map<String, String> hostAttributes
    Charset encoding

    GradleBuildComparisonResultHtmlRenderer(
            BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers,
            Charset encoding,
            ComparableGradleBuildExecuter sourceExecuter,
            ComparableGradleBuildExecuter targetExecuter,
            Map<String, String> hostAttributes,
            Transformer<String, File> fileRelativizer
    ) {
        this.renderers = renderers
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

                    h2 "Associated build outcomes"
                    p "Associated build outcomes are outcomes that have been identified as being intended to be the same between the target and source build."

                    ol {
                        for (comparison in result.comparisons) {
                            li {
                                // TODO: assuming that the names are unique and that they are always the same on both sides which they are in 1.2
                                a("class": context.diffClass(comparison.outcomesAreIdentical), href: "#${name(comparison)}", name(comparison))
                            }
                        }
                    }

                    for (BuildOutcomeComparisonResult comparison in result.comparisons) {
                        BuildOutcomeComparisonResultRenderer renderer = renderers.getRenderer(comparison.getClass())

                        if (renderer == null) {
                            throw new IllegalArgumentException(String.format("Cannot find renderer for build output comparison result type: %s", result))
                        }

                        div("class": "build-outcome-comparison text-container", id: name(comparison)) {
                            renderer.render(comparison, context)
                        }
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
                    .${context.diffClass} { color: red; }
                    .${context.equalClass} { color: green; }

                    .container {
                        padding: 30px;
                        background-color: #FFF7F7;
                        border-radius: 10px;
                        border: 1px solid #E0E0E0;
                    }

                    .build-outcome-comparison {
                        padding: 10px 10px 14px 20px;
                        border: 1px solid #D0D0D0;
                        border-radius: 6px;
                        margin-bottom: 1.2em;
                    }

                    .build-outcome-comparison :last-child {
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

                    def sourceGradleVersion = sourceBuild.gradleVersion.version
                    def targetGradleVersion = targetBuild.gradleVersion.version
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

    String name(BuildOutcomeComparisonResult<?> comparisonResult) {
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
