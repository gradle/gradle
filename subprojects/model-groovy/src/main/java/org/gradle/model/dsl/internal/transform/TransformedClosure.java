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

package org.gradle.model.dsl.internal.transform;

import org.gradle.model.dsl.internal.inputs.PotentialInputs;

import javax.annotation.Nullable;

/**
 * Implemented by transformed rules closure.
 */
public interface TransformedClosure {
    /**
     * Returns the input references for this closure.
     */
    InputReferences inputReferences();

    /**
     * Returns the source location for this closure.
     */
    SourceLocation sourceLocation();

    /**
     * Marks this closure as a rule action, with the given inputs
     *
     * @param nestedRuleFactory Factory to use to create nested rules.
     */
    void makeRule(PotentialInputs inputs, @Nullable ClosureBackedRuleFactory nestedRuleFactory);
}
