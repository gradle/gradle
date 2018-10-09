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

package org.gradle.internal.execution.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.snapshot.ValueSnapshot;

public abstract class AbstractExecutionSnapshot {
    private final ImplementationSnapshot implementation;
    private final ImmutableList<ImplementationSnapshot> additionalImplementations;
    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedSet<String> outputPropertyNames;

    public AbstractExecutionSnapshot(
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames
    ) {
        this.implementation = implementation;
        this.additionalImplementations = additionalImplementations;
        this.inputProperties = inputProperties;
        this.outputPropertyNames = outputPropertyNames;
    }

    public ImplementationSnapshot getImplementation() {
        return implementation;
    }

    public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
        return additionalImplementations;
    }

    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    public ImmutableSortedSet<String> getOutputPropertyNames() {
        return outputPropertyNames;
    }
}
