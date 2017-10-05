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

public abstract class AbstractTaskExecution implements TaskExecution {

    private final UniqueId buildInvocationId;
    private final ImplementationSnapshot taskImplementation;
    private final ImmutableList<ImplementationSnapshot> taskActionImplementations;
    private final ImmutableSortedMap<String, ValueSnapshot> inputProperties;
    private final ImmutableSortedSet<String> outputPropertyNamesForCacheKey;

    public AbstractTaskExecution(
        UniqueId buildInvocationId,
        ImplementationSnapshot taskImplementation,
        ImmutableList<ImplementationSnapshot> taskActionImplementations,
        ImmutableSortedMap<String, ValueSnapshot> inputProperties,
        ImmutableSortedSet<String> outputPropertyNames) {
        this.buildInvocationId = buildInvocationId;
        this.taskImplementation = taskImplementation;
        this.taskActionImplementations = taskActionImplementations;
        this.inputProperties = inputProperties;
        this.outputPropertyNamesForCacheKey = outputPropertyNames;
    }

    @Override
    public UniqueId getBuildInvocationId() {
        return buildInvocationId;
    }

    @Override
    public ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey() {
        return ImmutableSortedSet.copyOf(outputPropertyNamesForCacheKey);
    }

    @Override
    public ImplementationSnapshot getTaskImplementation() {
        return taskImplementation;
    }

    @Override
    public ImmutableList<ImplementationSnapshot> getTaskActionImplementations() {
        return taskActionImplementations;
    }

    @Override
    public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
        return inputProperties;
    }

}
