/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.tooling.events.OperationType;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Provides information about what events the build client is interested in.
 */
public class BuildClientSubscriptions implements Serializable {

    private final Set<OperationType> operationTypes;

    public BuildClientSubscriptions(Set<OperationType> operationTypes) {
        this.operationTypes = EnumSet.copyOf(operationTypes);
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
