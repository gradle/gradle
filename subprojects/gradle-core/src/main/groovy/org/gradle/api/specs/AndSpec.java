/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.specs;

import groovy.lang.Closure;
import org.gradle.util.GUtil;

import java.util.Arrays;

/**
 * A {@link org.gradle.api.specs.CompositeSpec} which requires all its specs to be true in order to evaluate to true.
 * Uses lazy evaluation.
 *
 * @author Hans Dockter
 * @param <T> The target type for this Spec
 */
public class AndSpec<T> extends CompositeSpec<T> {
    public AndSpec(Spec<? super T>... specs) {
        super(specs);
    }

    public AndSpec(Iterable<? extends Spec<? super T>> specs) {
        super(specs);
    }

    public boolean isSatisfiedBy(T object) {
        for (Spec<? super T> spec : getSpecs()) {
            if (!spec.isSatisfiedBy(object)) {
                return false;
            }
        }
        return true;
    }

    public AndSpec<T> and(Spec<? super T>... specs) {
        return new AndSpec<T>(GUtil.addLists(getSpecs(), Arrays.asList(specs)));
    }

    public AndSpec<T> and(Closure spec) {
        return and(Specs.<T>convertClosureToSpec(spec));
    }
}
