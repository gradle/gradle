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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.collect.PersistentSet;

abstract class DefaultCompositeExclude implements CompositeExclude {
    private final PersistentSet<ExcludeSpec> components;

    DefaultCompositeExclude(PersistentSet<ExcludeSpec> components) {
        this.components = components;
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
        return Objects.equal(components, that.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public PersistentSet<ExcludeSpec> getComponents() {
        return components;
    }

    @Override
    public int size() {
        return components.size();
    }

    @Override
    public String toString() {
        return "{\"" + getDisplayName() + "\": " +
            " " + components +
            '}';
    }

    protected abstract String getDisplayName();
}
