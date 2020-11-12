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

import java.util.Map;
import java.util.function.Function;

public abstract class AbstractCompareStrategy<C, S> {
    private final Function<C, ? extends Map<String, S>> indexer;
    private final ChangeDetector<S> changeDetector;

    public AbstractCompareStrategy(
        Function<C, ? extends Map<String, S>> indexer,
        ChangeDetector<S> changeDetector
    ) {
        this.indexer = indexer;
        this.changeDetector = changeDetector;
    }

    public boolean visitChangesSince(C previous, C current, String propertyTitle, ChangeVisitor visitor) {
        if (hasSameRootHashes(previous, current)) {
            return true;
        }
        return changeDetector.visitChangesSince(indexer.apply(previous), indexer.apply(current), propertyTitle, visitor);
    }

    protected abstract boolean hasSameRootHashes(C previous, C current);

    public interface ChangeDetector<S> {
        boolean visitChangesSince(Map<String, S> previous, Map<String, S> current, String propertyTitle, ChangeVisitor visitor);
    }

    public interface ChangeFactory<S> {
        Change added(String path, String propertyTitle, S current);
        Change removed(String path, String propertyTitle, S previous);
        Change modified(String path, String propertyTitle, S previous, S current);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
