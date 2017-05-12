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
 * An object that can compare two build outcomes of a common type.
 *
 * @param <T> The common type of the outcomes to be compared.
 * @param <R> The type of the result returned by the comparison.
 */
public interface BuildOutcomeComparator<T extends BuildOutcome, R extends BuildOutcomeComparisonResult<T>> {

    /**
     * The type of outcomes that this comparator can compare.
     *
     * @return The type of outcomes that this comparator can compare. Never null.
     */
    Class<T> getComparedType();

    /**
     * Compares the associated outcomes.
     *
     * @param association The associated outcomes to compare.
     * @return The result of the comparison. Never null.
     */
    R compare(BuildOutcomeAssociation<T> association);

}
