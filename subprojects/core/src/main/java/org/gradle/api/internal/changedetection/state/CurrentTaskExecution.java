/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

@NonNullApi
public class CurrentTaskExecution extends AbstractTaskExecution implements BeforeExecutionState {

    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints;
    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFingerprints;
    private final ImmutableSortedSet<String> outputPropertyNamesForCacheKey;

    public CurrentTaskExecution(
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFingerprints,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprintsBeforeExecution
    ) {
        super(taskImplementation, taskActionImplementations, inputProperties);
        this.outputFingerprints = outputFingerprintsBeforeExecution;
        this.inputFingerprints = inputFingerprints;
        this.outputPropertyNamesForCacheKey = outputPropertyNames;
    }

    /**
     * Returns the names of all cacheable output property names that have a value set.
     * The collection includes names of properties declared via mapped plural outputs,
     * and excludes optional properties that don't have a value set. If the task is not
     * cacheable, it returns an empty collection.
     */
    public ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey() {
        return outputPropertyNamesForCacheKey;
    }

    /**
     * The fingerprints of the output files for the current execution.
     *
     * @return The fingerprint of the output files before or after the task executed, depending on which one is available.
     */
    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFileProperties() {
        return outputFingerprints;
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
        return inputFingerprints;
    }
}
