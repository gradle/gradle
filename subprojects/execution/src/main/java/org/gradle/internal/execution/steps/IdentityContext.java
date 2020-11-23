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

public interface IdentityContext extends ExecutionRequestContext {
    /**
     * All currently known input properties.
     */
    ImmutableSortedMap<String, ValueSnapshot> getInputProperties();

    /**
     * All currently known input file properties.
     */
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties();

    /**
     * Returns an identity for the given work item that uniquely identifies it
     * among all the other work items of the same type in the current build.
     */
    Identity getIdentity();
}
