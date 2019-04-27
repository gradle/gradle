/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;

import java.util.List;

/**
 * This factory is responsible for optimizing in special cases: null parameters,
 * list with 2 elements, ... and should be at the top of the delegation chain.
 */
public class OptimizingExcludeFactory extends DelegatingExcludeFactory {
    public OptimizingExcludeFactory(ExcludeFactory delegate) {
        super(delegate);
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return Optimizations.optimizeAnyOf(one, two, super::anyOf);
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return Optimizations.optimizeAllOf(this, one, two, super::allOf);
    }

    @Override
    public ExcludeSpec anyOf(List<ExcludeSpec> specs) {
        return Optimizations.optimizeList(this, specs, list -> {
            if (list.size() == 2) {
                return delegate.anyOf(list.get(0), list.get(1));
            }
            return delegate.anyOf(list);
        });
    }

    @Override
    public ExcludeSpec allOf(List<ExcludeSpec> specs) {
        return Optimizations.optimizeList(this, specs, list -> {
            if (list.size() == 2) {
                return delegate.allOf(list.get(0), list.get(1));
            }
            return delegate.allOf(list);
        });
    }
}
