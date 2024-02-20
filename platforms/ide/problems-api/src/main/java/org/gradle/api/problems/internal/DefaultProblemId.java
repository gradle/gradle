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

import javax.annotation.Nullable;
import java.io.Serializable;

public class DefaultProblemId implements ProblemId, Serializable {

    private final String id;
    private final String displayName;
    private final DefaultProblemId parent;

    DefaultProblemId(String id, String displayName, ProblemGroup parent) {
        this.id = id;
        this.displayName = displayName;
        this.parent = parent == null ? null : copy(parent);
    }


    public static DefaultProblemId copy(ProblemGroup id) {
        if (id.getParent() == null) {
            return new DefaultProblemId(id.getId(), id.getDisplayName(), null); // TODO sort this out; check ValiadtionProblemSerializationTest
        } else {
            return new DefaultProblemId(id.getId(), id.getDisplayName(), copy(id.getParent()));
        }
    }

    public static ProblemId from(String id, String displayName, ProblemGroup parent) {
        return new DefaultProblemId(id, displayName, parent == null ? null : copy(parent));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    @Override
    public DefaultProblemId getParent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblemId that = (DefaultProblemId) o;
        return Objects.equal(id, that.id) && Objects.equal(displayName, that.displayName) && Objects.equal(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, displayName, parent);
    }
}
