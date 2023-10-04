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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

public class IdentityContext extends ExecutionRequestContext {

    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties;
    private final Identity identity;

    public IdentityContext(ExecutionRequestContext parent, ImmutableSortedMap<String, ValueSnapshot> inputProperties, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties, Identity identity) {
        super(parent);
        this.inputProperties = inputProperties;
        this.inputFileProperties = inputFileProperties;
        this.identity = identity;
    }

    protected IdentityContext(IdentityContext parent) {
        this(parent, parent.getInputProperties(), parent.getInputFileProperties(), parent.getIdentity());
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

    /**
     * Returns an identity for the given work item that uniquely identifies it
     * among all the other work items of the same type in the current build.
     */
    public Identity getIdentity() {
        return identity;
    }
}
