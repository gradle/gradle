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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class Optimizations {
    public static ExcludeSpec optimizeAnyOf(ExcludeSpec one, ExcludeSpec two, BiFunction<ExcludeSpec, ExcludeSpec, ExcludeSpec> onMiss) {
        // fast path for two
        if (one == null) {
            return two;
        }
        if (two == null) {
            return one;
        }
        if (one.equals(two)) {
            return one;
        }
        if (one instanceof ExcludeEverything) {
            return one;
        }
        if (one instanceof ExcludeNothing) {
            return two;
        }
        if (two instanceof ExcludeEverything) {
            return two;
        }
        if (two instanceof ExcludeNothing) {
            return one;
        }
        return onMiss.apply(one, two);
    }

    public static ExcludeSpec optimizeAllOf(ExcludeFactory factory, ExcludeSpec one, ExcludeSpec two, BiFunction<ExcludeSpec, ExcludeSpec, ExcludeSpec> onMiss) {
        // fast path for two
        if (one == null) {
            return two;
        }
        if (two == null) {
            return one;
        }
        if (one.equals(two)) {
            return one;
        }
        if (one instanceof ExcludeEverything) {
            return two;
        }
        if (one instanceof ExcludeNothing) {
            return factory.nothing();
        }
        if (two instanceof ExcludeEverything) {
            return one;
        }
        if (two instanceof ExcludeNothing) {
            return factory.nothing();
        }
        return onMiss.apply(one, two);
    }

    public static <T extends Collection<ExcludeSpec>> ExcludeSpec optimizeCollection(ExcludeFactory factory, T specs, Function<T, ExcludeSpec> onMiss) {
        if (specs.isEmpty()) {
            return factory.nothing();
        }
        if (specs.size() == 1) {
            return specs.iterator().next();
        }
        return onMiss.apply(specs);
    }
}
