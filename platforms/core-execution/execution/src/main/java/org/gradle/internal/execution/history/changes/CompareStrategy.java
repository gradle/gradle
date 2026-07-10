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

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import org.gradle.internal.hash.HashCode;

import java.util.Map;
import java.util.function.Function;

public class CompareStrategy<C, S> {
    private final Function<C, ? extends Map<String, S>> indexer;
    private final Function<C, ? extends Multimap<String, HashCode>> rootHasher;
    private final ChangeDetector<S> changeDetector;

    public CompareStrategy(
        Function<C, ? extends Map<String, S>> indexer,
        Function<C, ? extends Multimap<String, HashCode>> rootHasher,
        ChangeDetector<S> changeDetector
    ) {
        this.indexer = indexer;
        this.rootHasher = rootHasher;
        this.changeDetector = changeDetector;
    }

    public boolean visitChangesSince(C previous, C current, String propertyTitle, ChangeVisitor visitor) {
        if (Iterables.elementsEqual(rootHasher.apply(previous).entries(), rootHasher.apply(current).entries())) {
            return true;
        }
        return changeDetector.visitChangesSince(indexer.apply(previous), indexer.apply(current), propertyTitle, visitor);
    }

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
