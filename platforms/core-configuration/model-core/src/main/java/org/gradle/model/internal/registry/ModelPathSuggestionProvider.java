/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.registry;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.concurrent.ThreadSafe;
import java.util.List;

@ThreadSafe
class ModelPathSuggestionProvider implements Transformer<List<ModelPath>, ModelPath> {

    private static final Predicate<Suggestion> REMOVE_NULLS = new Predicate<Suggestion>() {
        @Override
        public boolean apply(Suggestion input) {
            return input != null;
        }
    };

    private final Iterable<ModelPath> availablePaths;

    public ModelPathSuggestionProvider(Iterable<ModelPath> availablePaths) {
        this.availablePaths = availablePaths;
    }

    @ThreadSafe
    private static class Suggestion implements Comparable<Suggestion> {
        private final int distance;
        private final ModelPath path;

        private Suggestion(int distance, ModelPath path) {
            this.distance = distance;
            this.path = path;
        }

        @Override
        public int compareTo(Suggestion o) {
            int distanceDifference = distance - o.distance;
            if (distanceDifference == 0) {
                return path.toString().compareTo(o.path.toString());
            } else {
                return distanceDifference;
            }
        }
    }

    @Override
    public List<ModelPath> transform(final ModelPath unavailable) {
        Iterable<Suggestion> suggestions = Iterables.transform(availablePaths, new Function<ModelPath, Suggestion>() {
            @Override
            public Suggestion apply(ModelPath available) {
                int distance = StringUtils.getLevenshteinDistance(unavailable.toString(), available.toString());
                boolean suggest = distance <= Math.min(3, unavailable.toString().length() / 2);
                if (suggest) {
                    return new Suggestion(distance, available);
                } else {
                    // avoid excess creation of Suggestion objects
                    return null;
                }
            }
        });

        suggestions = Iterables.filter(suggestions, REMOVE_NULLS);
        List<Suggestion> sortedSuggestions = CollectionUtils.sort(suggestions);
        return CollectionUtils.collect(sortedSuggestions, s -> s.path);
    }
}
