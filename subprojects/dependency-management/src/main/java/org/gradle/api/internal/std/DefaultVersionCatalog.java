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
package org.gradle.api.internal.std;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultVersionCatalog implements Serializable {
    private final String name;
    private final String description;
    private final Map<String, DependencyModel> aliasToDependency;
    private final Map<String, BundleModel> bundles;
    private final Map<String, VersionModel> versions;
    private final int hashCode;

    public DefaultVersionCatalog(String name,
                                 String description,
                                 Map<String, DependencyModel> aliasToDependency,
                                 Map<String, BundleModel> bundles,
                                 Map<String, VersionModel> versions) {
        this.name = name;
        this.description = description;
        this.aliasToDependency = aliasToDependency;
        this.bundles = bundles;
        this.versions = versions;
        this.hashCode = doComputeHashCode();
    }

    public String getName() {
        return name;
    }

    // Intentionally not part of state
    public String getDescription() {
        return description;
    }

    public List<String> getDependencyAliases() {
        return aliasToDependency.keySet()
            .stream()
            .sorted()
            .collect(Collectors.toList());
    }

    public List<String> getBundleAliases() {
        return bundles.keySet()
            .stream()
            .sorted()
            .collect(Collectors.toList());
    }

    public DependencyModel getDependencyData(String alias) {
        return aliasToDependency.get(alias);
    }

    public List<String> getVersionAliases() {
        return versions.keySet()
            .stream()
            .sorted()
            .collect(Collectors.toList());
    }

    public BundleModel getBundle(String name) {
        return bundles.get(name);
    }

    public VersionModel getVersion(String name) {
        return versions.get(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultVersionCatalog that = (DefaultVersionCatalog) o;

        if (!aliasToDependency.equals(that.aliasToDependency)) {
            return false;
        }
        if (!bundles.equals(that.bundles)) {
            return false;
        }
        return versions.equals(that.versions);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int doComputeHashCode() {
        int result = aliasToDependency.hashCode();
        result = 31 * result + bundles.hashCode();
        result = 31 * result + versions.hashCode();
        return result;
    }

    public boolean isNotEmpty() {
        return !(aliasToDependency.isEmpty() && bundles.isEmpty() && versions.isEmpty());
    }
}
