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

package org.gradle.model.dsl.internal.spike;

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;

import java.util.Map;
import java.util.Set;

public class ModelCreators {

    public static ModelCreator of(Object object) {
        return new ModelCreator(ImmutableSet.<String>of(), Transformers.constant(object));
    }

    public static ModelCreator resultOf(final Closure creatorClosure, Set<String> modelPaths) {
        Transformer<Object, Map<String, Object>> closureBackedCreator = new Transformer<Object, Map<String, Object>>() {
            public Object transform(Map<String, Object> inputs) {
                return creatorClosure.call(inputs);
            }
        };

        return new ModelCreator(modelPaths, closureBackedCreator);
    }

    public static ModelCreator resultOf(final Closure creatorClosure) {
        return resultOf(creatorClosure, ImmutableSet.<String>of());
    }
}
