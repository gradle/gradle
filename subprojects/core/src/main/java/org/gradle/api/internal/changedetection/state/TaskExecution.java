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
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.id.UniqueId;

/**
 * The state for a single task execution.
 */
public interface TaskExecution {

    UniqueId getBuildInvocationId();

    /**
     * Returns the names of all cacheable output property names that have a value set.
     * The collection includes names of properties declared via mapped plural outputs,
     * and excludes optional properties that don't have a value set. If the task is not
     * cacheable, it returns an empty collection.
     */
    ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey();

    ImplementationSnapshot getTaskImplementation();

    ImmutableList<ImplementationSnapshot> getTaskActionImplementations();

    ImmutableSortedMap<String, ValueSnapshot> getInputProperties();

    ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot();

    ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot();

    FileCollectionSnapshot getDiscoveredInputFilesSnapshot();

    boolean isSuccessful();

}
