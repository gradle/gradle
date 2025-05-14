/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalProblemGroup;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

@NullMarked
public class DefaultProblemGroup implements InternalProblemGroup, Serializable {

    private final String name;
    private final String displayName;
    private final InternalProblemGroup parent;

    public DefaultProblemGroup(String name, String displayName, @Nullable InternalProblemGroup parent) {
          this.name = name;
          this.displayName = displayName;
          this.parent = parent;
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
    public InternalProblemGroup getParent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultProblemGroup)) {
            return false;
        }
        DefaultProblemGroup that = (DefaultProblemGroup) o;
        return Objects.equals(name, that.name) && Objects.equals(displayName, that.displayName) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, displayName, parent);
    }
}
