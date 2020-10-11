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

public class AllDependenciesModel implements Serializable {
    private final Map<String, DependencyModel> aliasToDependency;
    private final Map<String, List<String>> bundles;
    private final int hashCode;

    public AllDependenciesModel(Map<String, DependencyModel> aliasToDependency, Map<String, List<String>> bundles) {
        this.aliasToDependency = aliasToDependency;
        this.bundles = bundles;
        this.hashCode = doComputeHashCode();
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

    List<String> getBundle(String name) {
        return bundles.get(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AllDependenciesModel that = (AllDependenciesModel) o;

        if (!aliasToDependency.equals(that.aliasToDependency)) {
            return false;
        }
        return bundles.equals(that.bundles);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int doComputeHashCode() {
        int result = aliasToDependency.hashCode();
        result = 31 * result + bundles.hashCode();
        return result;
    }
}
