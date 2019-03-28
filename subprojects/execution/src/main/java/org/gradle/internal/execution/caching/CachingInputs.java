/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.caching;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

public interface CachingInputs {

    /**
     * The snapshot of the main implementation.
     */
    ImplementationSnapshot getImplementation();

    /**
     * Any additional implementation present.
     */
    ImmutableList<ImplementationSnapshot> getAdditionalImplementations();

    /**
     * Input value fingerprints.
     */
    ImmutableSortedMap<String, HashCode> getInputValueFingerprints();

    /**
     * Input file fingerprints.
     */
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileFingerprints();

    /**
     * Names of the output properties of the work.
     */
    ImmutableSortedSet<String> getOutputProperties();

    /**
     * A list of input value property names that were not cacheable.
     */
    ImmutableSortedSet<String> getNonCacheableInputProperties();
}
