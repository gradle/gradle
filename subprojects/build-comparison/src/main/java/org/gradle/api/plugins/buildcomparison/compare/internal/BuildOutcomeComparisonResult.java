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

package org.gradle.api.plugins.buildcomparison.compare.internal;

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome;
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociation;

/**
 * The result of a comparison between to associated build outcomes.
 *
 * Implementors exposes information about how the outcomes were different.
 *
 * @param <T> The type of outcome that was compared.
 */
public interface BuildOutcomeComparisonResult<T extends BuildOutcome> {

    /**
     * The associated outcomes that were compared.
     *
     * @return The associated outcomes that were compared. Never null
     */
    BuildOutcomeAssociation<T> getCompared();

    /**
     * True if the outcomes are considered to be strictly identical, otherwise false.
     *
     * @return True if the outcomes are considered to be strictly identical, otherwise false.
     */
    boolean isOutcomesAreIdentical();
}
