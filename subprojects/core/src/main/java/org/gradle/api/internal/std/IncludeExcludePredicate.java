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
package org.gradle.api.internal.std;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Predicate;

public class IncludeExcludePredicate<T> implements Predicate<T> {
    private final Set<T> includes;
    private final Set<T> excludes;

    public static <T> IncludeExcludePredicate<T> of(@Nullable Set<T> includes, @Nullable Set<T> excludes) {
        return new IncludeExcludePredicate<>(includes, excludes);
    }

    public static <T> IncludeExcludePredicate<T> acceptAll() {
        return new IncludeExcludePredicate<>(null, null);
    }

    private IncludeExcludePredicate(@Nullable Set<T> includes, @Nullable Set<T> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    @Override
    public boolean test(T value) {
        if (includes != null && !includes.contains(value)) {
            return false;
        }
        if (excludes != null && excludes.contains(value)) {
            return false;
        }
        return true;
    }
}
