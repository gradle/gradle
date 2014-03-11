/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.util.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultModuleVersionListing implements ModuleVersionListing {
    private final Set<Versioned> versions = new HashSet<Versioned>();

    public DefaultModuleVersionListing(String version) {
        add(version);
    }

    public DefaultModuleVersionListing() {
    }

    public void add(String version) {
        versions.add(new DefaultAvailableVersion(version));
    }

    public Set<Versioned> getVersions() {
        return versions;
    }

    public boolean isEmpty() {
        return versions.isEmpty();
    }

    public List<Versioned> sortLatestFirst(LatestStrategy latestStrategy) {
        List<Versioned> sorted = latestStrategy.sort(versions);
        Collections.reverse(sorted);
        return sorted;
    }

    @Override
    public String toString() {
        return CollectionUtils.collect(versions, new Transformer<String, Versioned>() {
            public String transform(Versioned original) {
                return original.getVersion();
            }
        }).toString();
    }

    private static class DefaultAvailableVersion implements Versioned {
        private final String version;

        public DefaultAvailableVersion(String version) {
            this.version = version;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof DefaultAvailableVersion
                    && version.equals(((DefaultAvailableVersion) o).version);
        }

        @Override
        public int hashCode() {
            return version.hashCode();
        }

        @Override
        public String toString() {
            return String.format("AvailableVersion '%s'", version);
        }
    }
}
