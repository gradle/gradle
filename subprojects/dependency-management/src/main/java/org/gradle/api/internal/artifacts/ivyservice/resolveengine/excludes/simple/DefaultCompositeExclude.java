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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;

import java.util.Set;
import java.util.stream.Stream;

abstract class DefaultCompositeExclude implements CompositeExclude {
    private final ImmutableSet<ExcludeSpec> components;
    private final int hashCode;
    private final int size;

    DefaultCompositeExclude(ImmutableSet<ExcludeSpec> components) {
        this.components = components;
        this.size = components.size();
        this.hashCode = (31 * components.hashCode() + this.size) ^ mask();
    }

    abstract int mask();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultCompositeExclude that = (DefaultCompositeExclude) o;
        return hashCode == that.hashCode && Objects.equal(components, that.components);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public final Stream<ExcludeSpec> components() {
        return components.stream();
    }

    @Override
    public Set<ExcludeSpec> getComponents() {
        return components;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public String toString() {
        return "{\"" + getDisplayName() + "\": " +
            " " + components +
            '}';
    }

    protected abstract String getDisplayName();
}
