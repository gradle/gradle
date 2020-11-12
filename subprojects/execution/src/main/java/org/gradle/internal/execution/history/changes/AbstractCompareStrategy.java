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

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractCompareStrategy<C, S> {
    private final Function<C, ? extends Map<String, S>> indexer;

    public AbstractCompareStrategy(Function<C, ? extends Map<String, S>> indexer) {
        this.indexer = indexer;
    }

    public boolean visitChangesSince(C current, C previous, String propertyTitle, ChangeVisitor visitor) {
        if (hasSameRootHashes(current, previous)) {
            return true;
        }
        return visitChangesSince(indexer.apply(current), indexer.apply(previous), propertyTitle, visitor);
    }

    protected abstract boolean hasSameRootHashes(C previous, C current);

    protected abstract boolean hasSamePath(S previous, S current);

    protected abstract boolean hasSameContent(S previous, S current);

    protected abstract Change added(String path, String propertyTitle, S current);
    protected abstract Change removed(String path, String propertyTitle, S previous);
    protected abstract Change modified(String path, String propertyTitle, S previous, S current);

    private boolean visitChangesSince(Map<String, S> current, Map<String, S> previous, String propertyTitle, ChangeVisitor visitor) {
        // Handle trivial cases with 0 or 1 elements in both current and previous
        Boolean trivialResult = compareTrivialEntries(visitor, current, previous, propertyTitle);
        if (trivialResult != null) {
            return trivialResult;
        }
        return doVisitChangesSince(visitor, current, previous, propertyTitle);
    }

    protected abstract boolean doVisitChangesSince(ChangeVisitor visitor, Map<String, S> current, Map<String, S> previous, String propertyTitle);

    /**
     * Compares collection fingerprints if one of current or previous are empty or both have at most one element.
     *
     * @return {@code null} if the comparison is not trivial.
     * For a trivial comparision returns whether the {@link ChangeVisitor} is looking for further changes.
     * See {@link ChangeVisitor#visitChange(Change)}.
     */
    @VisibleForTesting
    @Nullable
    Boolean compareTrivialEntries(ChangeVisitor visitor, Map<String, S> current, Map<String, S> previous, String propertyTitle) {
        switch (current.size()) {
            case 0:
                if (previous.isEmpty()) {
                    return true;
                }
                for (Map.Entry<String, S> entry : previous.entrySet()) {
                    if (!visitor.visitChange(removed(entry.getKey(), propertyTitle, entry.getValue()))) {
                        return false;
                    }
                }
                return true;

            case 1:
                switch (previous.size()) {
                    case 0:
                        return reportAllAdded(visitor, current, propertyTitle);
                    case 1:
                        Map.Entry<String, S> previousEntry = previous.entrySet().iterator().next();
                        Map.Entry<String, S> currentEntry = current.entrySet().iterator().next();
                        return compareTrivialEntries(visitor, currentEntry, previousEntry, propertyTitle);
                    default:
                        return null;
                }

            default:
                if (!previous.isEmpty()) {
                    return null;
                }
                return reportAllAdded(visitor, current, propertyTitle);
        }
    }

    private boolean reportAllAdded(ChangeVisitor visitor, Map<String, S> current, String propertyTitle) {
        for (Map.Entry<String, S> entry : current.entrySet()) {
            if (!visitor.visitChange(added(entry.getKey(), propertyTitle, entry.getValue()))) {
                return false;
            }
        }
        return true;
    }

    private boolean compareTrivialEntries(ChangeVisitor visitor, Map.Entry<String, S> currentEntry, Map.Entry<String, S> previousEntry, String propertyTitle) {
        S previous = previousEntry.getValue();
        S current = currentEntry.getValue();
        if (hasSamePath(current, previous)) {
            if (hasSameContent(current, previous)) {
                return true;
            }
            String path = currentEntry.getKey();
            return visitor.visitChange(modified(path, propertyTitle, previous, current));
        } else {
            if (visitor.visitChange(removed(previousEntry.getKey(), propertyTitle, previous))) {
                return visitor.visitChange(added(currentEntry.getKey(), propertyTitle, current));
            }
            return false;
        }
    }
}
