/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.adapter;

import org.gradle.tooling.model.DomainObjectSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

class CollectionMapper {
    Collection<Object> createEmptyCollection(Class<?> collectionType) {
        if (collectionType.equals(DomainObjectSet.class)) {
            return new ArrayList<>();
        }
        if (collectionType.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>();
        }
        if (collectionType.isAssignableFrom(LinkedHashSet.class)) {
            return new LinkedHashSet<>();
        }
        if (collectionType.isAssignableFrom(TreeSet.class)) {
            return new TreeSet<>();
        }
        throw new UnsupportedOperationException(String.format("Cannot convert a Collection to type %s.", collectionType.getName()));
    }

    Map<Object, Object> createEmptyMap(Class<?> mapType) {
        if (mapType.isAssignableFrom(LinkedHashMap.class)) {
            return new LinkedHashMap<>();
        }
        if (mapType.isAssignableFrom(TreeMap.class)) {
            return new TreeMap<>();
        }
        throw new UnsupportedOperationException(String.format("Cannot convert a Map to type %s.", mapType.getName()));
    }
}
