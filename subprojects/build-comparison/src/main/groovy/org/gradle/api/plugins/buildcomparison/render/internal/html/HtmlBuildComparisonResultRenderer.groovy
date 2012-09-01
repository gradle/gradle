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

class HtmlBuildComparisonResultRenderer implements BuildComparisonResultRenderer<Writer> {

    private final PartRenderer headPart
    private final PartRenderer headerPart
    private final PartRenderer footerPart

    private final BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers

    HtmlBuildComparisonResultRenderer(
            BuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers,
            PartRenderer headPart,
            PartRenderer headerPart,
            PartRenderer footerPart
    ) {
        this.renderers = renderers
        this.headPart = headPart
        this.headerPart = headerPart
        this.footerPart = footerPart
    }

    void render(BuildComparisonResult result, Writer context) {
        MarkupBuilder markupBuilder = new MarkupBuilder(new IndentPrinter(context))
        HtmlRenderContext outcomeContext = new HtmlRenderContext(markupBuilder)

        markupBuilder.html {
            head {
                headPart?.render(result, outcomeContext)
            }
            body {
                div("class": "text-container") {
                    headerPart?.render(result, outcomeContext)

                    for (BuildOutcomeComparisonResult comparison in result.comparisons) {
                        BuildOutcomeComparisonResultRenderer renderer = renderers.getRenderer(comparison.getClass())

                        if (renderer == null) {
                            throw new IllegalArgumentException(String.format("Cannot find renderer for build output comparison result type: %s", result))
                        }

                        renderer.render(comparison, outcomeContext)
                    }

                    footerPart?.render(result, outcomeContext)
                }
            }
        }
    }
}
