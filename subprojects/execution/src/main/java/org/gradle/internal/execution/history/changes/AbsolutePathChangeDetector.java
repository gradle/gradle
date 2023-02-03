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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.execution.history.changes.CompareStrategy.ChangeDetector;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compares by absolute paths and file contents. Order does not matter.
 */
public class AbsolutePathChangeDetector<S> implements ChangeDetector<S> {

    private final ItemComparator<S> itemComparator;
    private final CompareStrategy.ChangeFactory<S> changeFactory;

    public AbsolutePathChangeDetector(ItemComparator<S> itemComparator, CompareStrategy.ChangeFactory<S> changeFactory) {
        this.itemComparator = itemComparator;
        this.changeFactory = changeFactory;
    }

    @Override
    public boolean visitChangesSince(Map<String, S> previous, Map<String, S> current, String propertyTitle, ChangeVisitor visitor) {
        Set<String> unaccountedForPreviousItems = new LinkedHashSet<>(previous.keySet());

        for (Map.Entry<String, S> currentEntry : current.entrySet()) {
            String currentAbsolutePath = currentEntry.getKey();
            S currentItem = currentEntry.getValue();
            if (unaccountedForPreviousItems.remove(currentAbsolutePath)) {
                S previousItem = previous.get(currentAbsolutePath);
                if (!itemComparator.hasSameContent(previousItem, currentItem)) {
                    Change modified = changeFactory.modified(currentAbsolutePath, propertyTitle, previousItem, currentItem);
                    if (!visitor.visitChange(modified)) {
                        return false;
                    }
                }
                // else, unchanged; check next file
            } else {
                Change added = changeFactory.added(currentAbsolutePath, propertyTitle, currentItem);
                if (!visitor.visitChange(added)) {
                    return false;
                }
            }
        }

        for (String previousAbsolutePath : unaccountedForPreviousItems) {
            Change removed = changeFactory.removed(previousAbsolutePath, propertyTitle, previous.get(previousAbsolutePath));
            if (!visitor.visitChange(removed)) {
                return false;
            }
        }
        return true;
    }

    public interface ItemComparator<S> {
        boolean hasSameContent(S previous, S current);
    }
}
