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

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

abstract class DefaultCompositeExclude implements CompositeExclude {
    private final ImmutableSet<ExcludeSpec> components;
    private final int hashCode;
    private final int size;

    DefaultCompositeExclude(ImmutableSet<ExcludeSpec> components) {
        this.components = components;
        this.size = components.size();
        this.hashCode = 31 * components.hashCode() + this.size ^ this.getClass().hashCode();
    }

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
    public boolean equalsIgnoreArtifact(ExcludeSpec o) {
        if (mayExcludeArtifacts()) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefaultCompositeExclude that = (DefaultCompositeExclude) o;
            return equalsIgnoreArtifact(components, that.components);
        }
        return equals(o);
    }

    private boolean equalsIgnoreArtifact(ImmutableSet<ExcludeSpec> components, ImmutableSet<ExcludeSpec> other) {
        if (components == other) {
            return true;
        }
        if (components.size() != other.size()) {
            return false;
        }
        return compareExcludingArtifacts(components, other);
    }

    private boolean compareExcludingArtifacts(ImmutableSet<ExcludeSpec> components, ImmutableSet<ExcludeSpec> other) {
        // The fast check iterator is there assuming that we have 2 collections with identical contents
        // in which case we can perform a faster check for sets, as if they were lists
        Iterator<ExcludeSpec> fastCheckIterator = other.iterator();
        boolean[] alreadyFound = new boolean[other.size()];
        int fastCheckCount = 0;
        for (ExcludeSpec component : components) {
            boolean found = false;
            if (fastCheckIterator != null) {
                if (fastCheckIterator.next().equalsIgnoreArtifact(component)) {
                    alreadyFound[fastCheckCount++] = true;
                    continue;
                } else if (!fastCheckIterator.hasNext()) {
                    // this was the last element, so we already know there's no possible match
                    return false;
                }
            }
            // we're unlucky, sets are either different, or in a different order
            fastCheckIterator = null;
            int innerCount = 0;
            // perform a first, quick check based on identity, in case it's just
            // a matter of ordering
            for (ExcludeSpec o : other) {
                if (!alreadyFound[innerCount] && component == o) {
                    found = true;
                    alreadyFound[innerCount] = true;
                    break;
                }
                innerCount++;
            }
            if (!found) {
                // slowest path when we can't find something which is the same instance
                innerCount = 0;
                for (ExcludeSpec o : other) {
                    if (!alreadyFound[innerCount] && component.equalsIgnoreArtifact(o)) {
                        found = true;
                        alreadyFound[innerCount] = true;
                        break;
                    }
                    innerCount++;
                }
            }
            if (!found) {
                // fast exit, when sets are actually different
                return false;
            }
        }
        return true;
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
        return "{" + getDisplayName() +
            " " + components +
            '}';
    }

    protected abstract String getDisplayName();
}
