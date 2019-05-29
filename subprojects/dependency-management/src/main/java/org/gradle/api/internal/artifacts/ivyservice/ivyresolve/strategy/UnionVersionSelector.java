/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ComponentMetadata;

import java.util.List;

/**
 * A version selector which is the union of other selectors. This is used by the
 * "rejects" clauses of version constraints, where each reject is turned into a
 * version selector, then the whole selector is the union of all of them.
 */
public class UnionVersionSelector implements CompositeVersionSelector {
    private final List<VersionSelector> selectors;

    public static UnionVersionSelector of(List<String> selectors, VersionSelectorScheme scheme) {
        ImmutableList.Builder<VersionSelector> builder = new ImmutableList.Builder<VersionSelector>();
        for (String selector : selectors) {
            builder.add(scheme.parseSelector(selector));
        }
        return new UnionVersionSelector(builder.build());
    }

    public UnionVersionSelector(List<VersionSelector> selectors) {
        this.selectors = selectors;
    }

    @Override
    public boolean isDynamic() {
        for (VersionSelector selector : selectors) {
            if (selector.isDynamic()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean requiresMetadata() {
        for (VersionSelector selector : selectors) {
            if (selector.requiresMetadata()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matchesUniqueVersion() {
        for (VersionSelector selector : selectors) {
            if (!selector.matchesUniqueVersion()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean accept(String candidate) {
        for (VersionSelector selector : selectors) {
            if (selector.accept(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean accept(Version candidate) {
        for (VersionSelector selector : selectors) {
            if (selector.accept(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean accept(ComponentMetadata candidate) {
        for (VersionSelector selector : selectors) {
            if (selector.accept(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canShortCircuitWhenVersionAlreadyPreselected() {
        for (VersionSelector selector : selectors) {
            if (!selector.canShortCircuitWhenVersionAlreadyPreselected()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getSelector() {
        throw new UnsupportedOperationException("Union selectors should only be used internally and don't provide a public string representation");
    }

    @Override
    public List<VersionSelector> getSelectors() {
        return selectors;
    }
}
