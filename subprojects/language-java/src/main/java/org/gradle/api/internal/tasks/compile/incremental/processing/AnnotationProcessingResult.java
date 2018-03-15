/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.processing;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AnnotationProcessingResult implements Serializable {

    private HashMap<String, Set<String>> generatedTypesByOrigin = new LinkedHashMap<String, Set<String>>();
    private Set<String> aggregatedTypes = new HashSet<String>();

    public void addGeneratedType(String name, Set<String> originatingElements) {
        for (String originatingElement : originatingElements) {
            Set<String> derived = generatedTypesByOrigin.get(originatingElement);
            if (derived == null) {
                derived = new LinkedHashSet<String>();
                generatedTypesByOrigin.put(originatingElement, derived);
            }
            derived.add(name);
        }
    }

    public Map<String, Set<String>> getGeneratedTypesByOrigin() {
        return generatedTypesByOrigin;
    }

    public void addAggregatedTypes(Set<String> aggregatedTypes) {
        this.aggregatedTypes.addAll(aggregatedTypes);
    }

    public Set<String> getAggregatedTypes() {
        return aggregatedTypes;
    }
}
