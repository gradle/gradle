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

package org.gradle.api.buildcomparison.outcome.string

import org.gradle.api.buildcomparison.render.internal.BuildOutcomeComparisonResultRenderer

class StringBuildOutcomeComparisonResultMapRenderer implements BuildOutcomeComparisonResultRenderer<org.gradle.api.buildcomparison.outcome.string.StringBuildOutcomeComparisonResult, Map> {

    Class<org.gradle.api.buildcomparison.outcome.string.StringBuildOutcomeComparisonResult> getResultType() {
        org.gradle.api.buildcomparison.outcome.string.StringBuildOutcomeComparisonResult
    }

    Class<Map> getContextType() {
        Map
    }

    void render(org.gradle.api.buildcomparison.outcome.string.StringBuildOutcomeComparisonResult result, Map context) {
        context.from = result.compared.from.value
        context.to = result.compared.to.value
        context.distance = result.distance
    }
}
