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

package org.gradle.api.internal.changedetection.rules;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

public class SortedMapDiffUtil {

    private SortedMapDiffUtil() {}

    public static <K, V> void diff(SortedMap<K, V> previous, SortedMap<K, V> current, PropertyDiffListener<K, V> diffListener) {
        Iterator<Map.Entry<K, V>> currentEntries = current.entrySet().iterator();
        Iterator<Map.Entry<K, V>> previousEntries = previous.entrySet().iterator();
        Comparator<? super K> comparator = previous.comparator();

        if (currentEntries.hasNext() && previousEntries.hasNext()) {
            Map.Entry<K, V> currentEntry = currentEntries.next();
            Map.Entry<K, V> previousEntry = previousEntries.next();
            while (true) {
                K previousProperty = previousEntry.getKey();
                K currentProperty = currentEntry.getKey();
                int compared = comparator.compare(previousProperty, currentProperty);
                if (compared < 0) {
                    diffListener.removed(previousProperty);
                    if (previousEntries.hasNext()) {
                        previousEntry = previousEntries.next();
                    } else {
                        diffListener.added(currentProperty);
                        break;
                    }
                } else if (compared > 0) {
                    diffListener.added(currentProperty);
                    if (currentEntries.hasNext()) {
                        currentEntry = currentEntries.next();
                    } else {
                        diffListener.removed(previousProperty);
                        break;
                    }
                } else {
                    diffListener.updated(previousProperty, previousEntry.getValue(), currentEntry.getValue());
                    if (previousEntries.hasNext() && currentEntries.hasNext()) {
                        previousEntry = previousEntries.next();
                        currentEntry = currentEntries.next();
                    } else {
                        break;
                    }
                }
            }
        }

        while (currentEntries.hasNext()) {
            diffListener.added(currentEntries.next().getKey());
        }

        while(previousEntries.hasNext()) {
            diffListener.removed(previousEntries.next().getKey());
        }
    }
}
