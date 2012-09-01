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

import org.gradle.api.plugins.buildcomparison.render.internal.BuildOutcomeComparisonResultRenderer
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResult

abstract class BuildOutcomeComparisonResultHtmlRenderer<T extends BuildOutcomeComparisonResult> implements BuildOutcomeComparisonResultRenderer<T, HtmlRenderContext> {

    BuildOutcomeComparisonResultHtmlRenderer(String fromSideName, String toSideName) {
        this.fromSideName = fromSideName
        this.toSideName = toSideName
    }

    final String fromSideName
    final String toSideName

    Class<HtmlRenderContext> getContextType() {
        HtmlRenderContext
    }

    protected void renderTitle(T result, HtmlRenderContext context) {
        def from = result.compared.from
        def to = result.compared.to

        // TODO - assuming that both sides have the same name, which they always do in 1.2
        context.render { h3 "${from.name}" }
    }


}
