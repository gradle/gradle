/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public interface BuildableComponentSelectionResult extends ComponentSelectionResult {
    static enum Reason {
        MATCH, NO_MATCH, CANNOT_DETERMINE
    }

    /**
     * Checks if a component could be chosen, more specifically the reason is {@link Reason#MATCH}.
     *
     * @return Flag
     */
    boolean hasMatch();

    /**
     * Checks if a component has no match, more specifically the reason is {@link Reason#NO_MATCH}.
     *
     * @return Flag
     */
    boolean hasNoMatch();

    /**
     * Marks the given module component identifier as matching.
     *
     * @param moduleComponentIdentifier Chosen module component identifier
     */
    void matches(ModuleComponentIdentifier moduleComponentIdentifier);

    /**
     * Registers that there was no matching module component identifier.
     */
    void noMatch();

    /**
     * Registers that no matching module component identifier could be determined.
     */
    void cannotDetermine();

    /**
     * Returns the reason for choosing the component.
     */
    Reason getReason();
}
