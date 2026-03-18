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

import com.google.common.base.Objects;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class DefaultProblemId extends ProblemId implements Serializable {

    private final String name;
    private final String displayName;
    private final ProblemGroup parent;

    public DefaultProblemId(String name, String displayName, ProblemGroup parent) {
        validateFields(name, displayName, parent);
        this.name = TextUtil.replaceLineSeparatorsOf(name, "");
        this.displayName = TextUtil.replaceLineSeparatorsOf(displayName, "");
        this.parent = parent;
    }

    private static void validateFields(String name, String displayName, ProblemGroup parent) {
        if (TextUtil.isBlank(name)) {
            throw new IllegalArgumentException("Problem id name must not be blank");
        }
        if (TextUtil.isBlank(displayName)) {
            throw new IllegalArgumentException("Problem id displayName must not be blank");
        }
        if (parent == null) {
            throw new IllegalArgumentException("Problem id parent must not be null");
        }
    }

    @Override
    public String getName() {
        return name;
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

        if (!name.equals(that.getName())) {
            return false;
        }
        return parent.equals(that.getGroup());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, parent);
    }
}
