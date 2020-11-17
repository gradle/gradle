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

package org.gradle.internal.execution.history.changes;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

public class SortedMapDiffUtil {

    private SortedMapDiffUtil() {}

    public static <K, VP, VC> boolean diff(SortedMap<K, ? extends VP> previous, SortedMap<K, ? extends VC> current, PropertyDiffListener<K, VP, VC> diffListener) {
        Iterator<? extends Map.Entry<K, ? extends VC>> currentEntries = current.entrySet().iterator();
        Iterator<? extends Map.Entry<K, ? extends VP>> previousEntries = previous.entrySet().iterator();
        Comparator<? super K> comparator = previous.comparator();

        if (currentEntries.hasNext() && previousEntries.hasNext()) {
            Map.Entry<K, ? extends VC> currentEntry = currentEntries.next();
            Map.Entry<K, ? extends VP> previousEntry = previousEntries.next();
            while (true) {
                K previousProperty = previousEntry.getKey();
                K currentProperty = currentEntry.getKey();
                int compared = comparator.compare(previousProperty, currentProperty);
                if (compared < 0) {
                    if (!diffListener.removed(previousProperty)) {
                        return false;
                    }
                    if (previousEntries.hasNext()) {
                        previousEntry = previousEntries.next();
                    } else {
                        if (!diffListener.added(currentProperty)) {
                            return false;
                        }
                        break;
                    }
                } else if (compared > 0) {
                    if (!diffListener.added(currentProperty)) {
                        return false;
                    }
                    if (currentEntries.hasNext()) {
                        currentEntry = currentEntries.next();
                    } else {
                        if (!diffListener.removed(previousProperty)) {
                            return false;
                        }
                        break;
                    }
                } else {
                    if (!diffListener.updated(previousProperty, previousEntry.getValue(), currentEntry.getValue())) {
                        return false;
                    }
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
            if (!diffListener.added(currentEntries.next().getKey())) {
                return false;
            }
        }

        while (previousEntries.hasNext()) {
            if (!diffListener.removed(previousEntries.next().getKey())) {
                return false;
            }
        }
        return true;
    }
}
