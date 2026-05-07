/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public class IdentityContext extends ExecutionRequestContext implements IdentifyingContext {

    private final ImplementationSnapshot implementation;
    private final ImmutableList<ImplementationSnapshot> additionalImplementations;
    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties;
    private final Identity identity;

    public IdentityContext(
        ExecutionRequestContext parent,
        ImplementationSnapshot implementation,
        ImmutableList<ImplementationSnapshot> additionalImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,
        Identity identity) {
        super(parent);
        this.implementation = implementation;
        this.additionalImplementations = additionalImplementations;
        this.inputProperties = inputProperties;
        this.inputFileProperties = inputFileProperties;
        this.identity = identity;
    }

    protected IdentityContext(IdentityContext parent) {
        this(
            parent,
            parent.getImplementation(),
            parent.getAdditionalImplementations(),
            parent.getInputProperties(),
            parent.getInputFileProperties(),
            parent.getIdentity()
        );
    }

    public ImplementationSnapshot getImplementation() {
        return implementation;
    }

    public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
        return additionalImplementations;
    }

    /**
     * All currently known input properties.
     */
    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

    /**
     * All currently known input file properties.
     */
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
        return inputFileProperties;
    }

    @Override
    public Identity getIdentity() {
        return identity;
    }
}
