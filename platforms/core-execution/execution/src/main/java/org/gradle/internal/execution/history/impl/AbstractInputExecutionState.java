/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.ExecutionInputState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public class AbstractInputExecutionState<C extends FileCollectionFingerprint> implements ExecutionInputState {
    protected final ImplementationSnapshot implementation;
    protected final ImmutableList<ImplementationSnapshot> additionalImplementations;
    protected final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    protected final ImmutableSortedMap<String, C> inputFileProperties;

    public AbstractInputExecutionState(
            ImplementationSnapshot implementation,
            ImmutableList<ImplementationSnapshot> additionalImplementations,
            ImmutableSortedMap<String, ValueSnapshot> inputProperties,
            ImmutableSortedMap<String, C> inputFileProperties
    ) {
        this.implementation = implementation;
        this.additionalImplementations = additionalImplementations;
        this.inputProperties = inputProperties;
        this.inputFileProperties = inputFileProperties;
    }

    @Override
    public ImplementationSnapshot getImplementation() {
        return implementation;
    }

    @Override
    public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
        return additionalImplementations;
    }

    @Override
    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    @Override
    public ImmutableSortedMap<String, C> getInputFileProperties() {
        return inputFileProperties;
    }
}
