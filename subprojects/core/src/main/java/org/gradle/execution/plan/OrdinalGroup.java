/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import javax.annotation.Nullable;

/**
 * Represents a set of nodes reachable from a particular entry point node (a "requested task")
 */
public class OrdinalGroup extends NodeGroup {
    private final int ordinal;

    public OrdinalGroup(int ordinal) {
        this.ordinal = ordinal;
    }

    @Override
    public String toString() {
        return "task group " + ordinal;
    }

    @Nullable
    @Override
    public OrdinalGroup asOrdinal() {
        return this;
    }

    @Override
    public NodeGroup withOrdinalGroup(OrdinalGroup newOrdinal) {
        return newOrdinal;
    }

    @Override
    public boolean isReachableFromEntryPoint() {
        return true;
    }

    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        OrdinalGroup that = (OrdinalGroup) o;

        return ordinal == that.ordinal;
    }

    @Override
    public int hashCode() {
        return ordinal;
    }
}
