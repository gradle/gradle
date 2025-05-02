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

package org.gradle.internal.build.event;

import com.google.common.collect.ImmutableSet;
import org.gradle.tooling.events.OperationType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Provides information about what events the build client is interested in.
 */
public class BuildEventSubscriptions {

    private final Set<OperationType> operationTypes;

    public BuildEventSubscriptions(Set<OperationType> operationTypes) {
        this.operationTypes = ImmutableSet.copyOf(operationTypes);
    }

    public Set<OperationType> getOperationTypes() {
        return operationTypes;
    }

    public boolean isRequested(OperationType workItem) {
        return operationTypes.contains(workItem);
    }

    public boolean isAnyOperationTypeRequested() {
        return !operationTypes.isEmpty();
    }

    public boolean isAnyRequested(OperationType... types) {
        return !isNoneRequested(types);
    }

    private boolean isNoneRequested(OperationType... types) {
        return Collections.disjoint(operationTypes, Arrays.asList(types));
    }

}
