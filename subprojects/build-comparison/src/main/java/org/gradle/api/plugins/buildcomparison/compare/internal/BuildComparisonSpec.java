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

import java.util.List;
import java.util.Set;

/**
 * A specification of how two sets of build outcomes are to be compared.
 *
 * Instances should be read only.
 */
public interface BuildComparisonSpec {

    /**
     * The complete list of outcomes from the from side.
     *
     * The returned collection is always immutable.
     *
     * @return The complete (immutable) list of outcomes from the from side. Never null.
     */
    Set<BuildOutcome> getSource();

    /**
     * The complete list of outcomes from the to side.
     *
     *
     * @return The complete (immutable) list of outcomes from the to side. Never null.
     */
    Set<BuildOutcome> getTarget();

    /**
     * The association between build outcomes from both sides.
     *
     * The outcomes should be compared in the order of the list.
     * <p>
     * Not all of the outcomes in the from and to sets will necessarily need be associated.
     *
     * @return The (immutable) list of associated build outcomes.
     */
    List<BuildOutcomeAssociation<?>> getOutcomeAssociations();

}
