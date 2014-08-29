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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class ModelPathSuggestionProvider implements Transformer<List<ModelPath>, ModelPath> {

    private final Iterable<ModelPath> availablePaths;

    public ModelPathSuggestionProvider(Iterable<ModelPath> availablePaths) {
        this.availablePaths = availablePaths;
    }

    public List<ModelPath> transform(final ModelPath original) {
        List<ModelPath> suggestions = CollectionUtils.filter(availablePaths, new ArrayList<ModelPath>(), new Spec<ModelPath>() {
            public boolean isSatisfiedBy(ModelPath element) {
                return StringUtils.getLevenshteinDistance(original.toString(), element.toString()) <= Math.min(3, original.toString().length() / 2);
            }
        });
        return CollectionUtils.sort(suggestions, new Comparator<ModelPath>() {
            public int compare(ModelPath firstPath, ModelPath secondPath) {
                String first = firstPath.toString();
                String second = secondPath.toString();
                int result = first.length() - second.length();
                if (result == 0) {
                    result = first.compareTo(second);
                }
                return result;
            }
        });
    }
}
