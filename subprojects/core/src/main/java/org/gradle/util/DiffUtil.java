/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.apache.commons.lang.ObjectUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DiffUtil {
    public static <T> void diff(Set<T> newSet, Set<T> oldSet, ChangeListener<? super T> changeListener) {
        Set<T> added = new HashSet<T>(newSet);
        added.removeAll(oldSet);
        for (T t : added) {
            changeListener.added(t);
        }

        Set<T> removed = new HashSet<T>(oldSet);
        removed.removeAll(newSet);
        for (T t : removed) {
            changeListener.removed(t);
        }
    }
    
    public static <K, V> void diff(Map<K, V> newMap, Map<K, V> oldMap, ChangeListener<? super Map.Entry<K, V>> changeListener) {
        Map<K, V> added = new HashMap<K, V>(newMap);
        added.keySet().removeAll(oldMap.keySet());
        for (Map.Entry<K, V> entry : added.entrySet()) {
            changeListener.added(entry);
        }

        Map<K, V> removed = new HashMap<K, V>(oldMap);
        removed.keySet().removeAll(newMap.keySet());
        for (Map.Entry<K, V> entry : removed.entrySet()) {
            changeListener.removed(entry);
        }

        Map<K, V> same = new HashMap<K, V>(newMap);
        same.keySet().retainAll(oldMap.keySet());
        for (Map.Entry<K, V> entry : same.entrySet()) {
            if (!ObjectUtils.equals(entry.getValue(), oldMap.get(entry.getKey()))) {
                changeListener.changed(entry);
            }
        }
    }
}
