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

package org.gradle.api.plugins.buildcomparison.outcome.internal.unknown

import org.gradle.api.plugins.buildcomparison.render.internal.html.BuildOutcomeHtmlRenderer
import org.gradle.api.plugins.buildcomparison.render.internal.html.HtmlRenderContext

class UnknownBuildOutcomeHtmlRenderer extends BuildOutcomeHtmlRenderer<UnknownBuildOutcome> {

    final Class<UnknownBuildOutcome> outcomeType = UnknownBuildOutcome

    void render(UnknownBuildOutcome outcome, HtmlRenderContext context) {
        renderTitle(outcome, context)

        context.render {
            p "This version of Gradle does not understand this kind of build outcome."
            p "Running the comparison process from a newer version of Gradle may yield better results."
        }
    }
}
