/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.hash.Hasher;

import java.util.Collection;

/**
 * This represents a composite resource entry filter which ignores the entry if any of the filters would ignore the entry.
 */
public class UnionResourceEntryFilter implements ResourceEntryFilter {
    private final Collection<ResourceEntryFilter> filters;

    public UnionResourceEntryFilter(Collection<ResourceEntryFilter> filters) {
        this.filters = filters;
    }

    @Override
    public boolean shouldBeIgnored(String entry) {
        return filters.stream().anyMatch(resourceEntryFilter -> resourceEntryFilter.shouldBeIgnored(entry));
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        filters.forEach(resourceEntryFilter -> resourceEntryFilter.appendConfigurationToHasher(hasher));
    }
}
