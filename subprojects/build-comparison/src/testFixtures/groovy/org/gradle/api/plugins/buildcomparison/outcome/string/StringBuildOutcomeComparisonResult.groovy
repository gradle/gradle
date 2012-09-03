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

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociation
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResultSupport

import static org.apache.commons.lang.StringUtils.getLevenshteinDistance

class StringBuildOutcomeComparisonResult extends BuildOutcomeComparisonResultSupport<StringBuildOutcome> {

    StringBuildOutcomeComparisonResult(BuildOutcomeAssociation<StringBuildOutcome> compared) {
        super(compared)
    }

    int getDistance() {
        getLevenshteinDistance(compared.source.value, compared.target.value)
    }

    boolean isOutcomesAreIdentical() {
        distance != 0
    }
}
