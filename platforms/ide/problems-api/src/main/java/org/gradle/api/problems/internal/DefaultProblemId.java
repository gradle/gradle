/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;

import javax.annotation.Nullable;
import java.io.Serializable;

public class DefaultProblemId implements ProblemId, Serializable {

    private final String id;
    private final String displayName;
    private final ProblemGroup parent;

    public DefaultProblemId(String id, String displayName, ProblemGroup parent) {
        this.id = id;
        this.displayName = displayName;
        this.parent = parent;
    }

    @Override
    public String getName() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public ProblemGroup getGroup() {
        return parent;
    }

    @Override
    public String toString() {
        return groupPath(getGroup()) + getName();
    }

    static String groupPath(@Nullable ProblemGroup group) {
        if (group == null) {
            return "";
        }
        ProblemGroup parent = group.getParent();
        return groupPath(parent) + group.getName() + ":";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass().isAssignableFrom(ProblemId.class)) {
            return false;
        }

        ProblemId that = (ProblemId) o;

        if (!id.equals(that.getName())) {
            return false;
        }
        return parent.equals(that.getGroup());
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + parent.hashCode();
        if (displayName != null) {
            result = 31 * result + displayName.hashCode();
        }
        return result;
    }
}
