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

import org.gradle.api.Incubating;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;


@Incubating
public class DefaultProblemGroup extends ProblemGroup implements ProblemGroupInternal, Serializable {

    private final String name;
    private final String displayName;
    @Nullable
    private final ProblemGroup parent;

    public DefaultProblemGroup(String groupId, String displayName) {
        this(groupId, displayName, null);
    }

    public DefaultProblemGroup(String name, String displayName, @Nullable ProblemGroup parent) {
        validateFields(name, displayName);
        this.name = TextUtil.replaceLineSeparatorsOf(name, "");
        this.displayName = TextUtil.replaceLineSeparatorsOf(displayName, "");
        this.parent = parent;
    }

    private static void validateFields(String name, String displayName) {
        if (TextUtil.isBlank(name)) {
            throw new IllegalArgumentException("Problem group name must not be blank");
        }
        if (TextUtil.isBlank(displayName)) {
            throw new IllegalArgumentException("Problem group displayName must not be blank");
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

    @Nullable
    @Override
    public ProblemGroup getParent() {
        return parent;
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return ProblemGroupSupport.equals(this, o);
    }

    @Override
    public int hashCode() {
        return ProblemGroupSupport.hashCode(this);
    }
}
