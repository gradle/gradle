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

package org.gradle.api.plugins.buildcomparison.outcome.string

import org.gradle.api.plugins.buildcomparison.render.internal.html.BuildOutcomeComparisonResultHtmlRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.html.HtmlRenderContext

class StringBuildOutcomeComparisonResultHtmlRenderer extends BuildOutcomeComparisonResultHtmlRenderer<StringBuildOutcomeComparisonResult> {

    Class<StringBuildOutcomeComparisonResult> getResultType() {
        StringBuildOutcomeComparisonResult
    }

    void render(StringBuildOutcomeComparisonResult result, HtmlRenderContext context) {
        context.render {
            table {
                tr {
                    th "Source"
                    th "Target"
                    th "Distance"
                }
                tr {
                    td result.compared.source.value
                    td result.compared.target.value
                    td result.distance
                }
            }
        }
    }
}
