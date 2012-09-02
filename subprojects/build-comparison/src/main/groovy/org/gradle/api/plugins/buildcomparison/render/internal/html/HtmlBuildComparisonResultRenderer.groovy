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

class HtmlBuildComparisonResultRenderer implements BuildComparisonResultRenderer<Writer> {

    private final BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers

    private final PartRenderer headPart
    private final PartRenderer headerPart
    private final PartRenderer footerPart

    Transformer<String, File> relativizer

    HtmlBuildComparisonResultRenderer(
            BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers,
            PartRenderer headPart,
            PartRenderer headerPart,
            PartRenderer footerPart,
            Transformer<String, File> relativizer
    ) {
        this.renderers = renderers
        this.headPart = headPart
        this.headerPart = headerPart
        this.footerPart = footerPart
        this.relativizer = relativizer
    }

    void render(BuildComparisonResult result, Writer writer) {
        MarkupBuilder markupBuilder = new MarkupBuilder(new IndentPrinter(writer))
        HtmlRenderContext context = new HtmlRenderContext(markupBuilder, relativizer)

        markupBuilder.html {
            head {
                headPart?.render(result, context)
            }
            body {
                div("class": "text-container") {
                    headerPart?.render(result, context)

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

                    footerPart?.render(result, context)
                }
            }
        }
    }

    String name(BuildOutcomeComparisonResult<?> comparisonResult) {
        comparisonResult.compared.from.name
    }
}
