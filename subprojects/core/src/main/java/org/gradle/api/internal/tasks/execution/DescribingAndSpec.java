/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import groovy.lang.Closure;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.CompositeSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.internal.ClosureSpec;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

/**
 * A {@link CompositeSpec} that requires all component specs to provide a description.
 * The aggregation is based on an {@link AndSpec}.
 *
 * @param <T> The target type for this Spec
 */
public class DescribingAndSpec<T> extends CompositeSpec<T> {
    private static final DescribingAndSpec<?> EMPTY = new DescribingAndSpec<>();
    private final AndSpec<T> specHolder;

    private DescribingAndSpec(AndSpec<T> specHolder) {
        super(specHolder.getSpecs().toArray(Cast.uncheckedCast(new Spec[0])));
        this.specHolder = specHolder;
    }

    public DescribingAndSpec() {
        this(AndSpec.empty());
    }

    public DescribingAndSpec(Spec<? super T> spec, String description) {
        this(new AndSpec<>(new SelfDescribingSpec<>(spec, description)));
    }

    @Override
    public boolean isSatisfiedBy(T element) {
        return specHolder.isSatisfiedBy(element);
    }

    @Nullable
    public SelfDescribingSpec<? super T> findUnsatisfiedSpec(T element) {
        return Cast.uncheckedCast(specHolder.findUnsatisfiedSpec(element));
    }

    public DescribingAndSpec<T> and(Spec<? super T> spec, String description) {
        return new DescribingAndSpec<>(specHolder.and(new SelfDescribingSpec<>(spec, description)));
    }

    @SuppressWarnings("rawtypes")
    public DescribingAndSpec<T> and(Closure closure, String description) {
        return and(new ClosureSpec<>(closure), description);
    }

    public static <T> DescribingAndSpec<T> empty() {
        return Cast.uncheckedNonnullCast(EMPTY);
    }
}
