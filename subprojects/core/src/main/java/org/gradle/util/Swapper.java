/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util;

import org.gradle.api.Action;
import org.gradle.internal.Factory;

/**
 * Generic utility for temporarily changing something.
 */
public class Swapper<T> {

    private final Factory<? extends T> getter;
    private final Action<? super T> setter;

    public Swapper(Factory<? extends T> getter, Action<? super T> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    public <Y extends T, N> N swap(Y value, Factory<N> whileSwapped) {
        T originalValue = getter.create();
        setter.execute(value);
        try {
            return whileSwapped.create();
        } finally {
            setter.execute(originalValue);
        }
    }
}
