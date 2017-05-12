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

package org.gradle.api.plugins.buildcomparison.outcome.internal;

/**
 * Associates to related build outcomes that can be compared.
 *
 * @param <T> The common type of the build outcomes associated
 */
public interface BuildOutcomeAssociation<T extends BuildOutcome> {

    /**
     * The outcome on the from side.
     *
     * @return The outcome on the from side. Never null.
     */
    T getSource();

    /**
     * The outcome on the to side.
     *
     * @return The outcome on the from side. Never null.
     */
    T getTarget();

    /**
     * The common type of the from and to outcomes.
     *
     * The from and to outcomes may be subtypes or implementors of this type. This type
     * will be used to determine how they should be compared.
     *
     * @return The common type of the from and to outcomes. Never null.
     */
    Class<T> getType();

}
