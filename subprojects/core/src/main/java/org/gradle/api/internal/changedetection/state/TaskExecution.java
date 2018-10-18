/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

/**
 * The state for a single task execution.
 */
public interface TaskExecution {

    OriginMetadata getOriginExecutionMetadata();

    ImplementationSnapshot getTaskImplementation();

    ImmutableList<ImplementationSnapshot> getTaskActionImplementations();

    ImmutableSortedMap<String, ValueSnapshot> getInputProperties();

    ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getOutputFingerprints();

    ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getInputFingerprints();

    boolean isSuccessful();

}
