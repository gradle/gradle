/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.util.internal;

import com.google.common.collect.Interner;
import com.google.common.collect.Maps;

import java.util.Map;

public class SimpleMapInterner implements Interner<String> {
    private final Map<String, String> internedStrings;

    private SimpleMapInterner(Map<String, String> interned) {
        this.internedStrings = interned;
    }

    public static SimpleMapInterner notThreadSafe() {
        return new SimpleMapInterner(Maps.<String, String>newHashMap());
    }

    public static SimpleMapInterner threadSafe() {
        return new SimpleMapInterner(Maps.<String, String>newConcurrentMap());
    }

    @Override
    @SuppressWarnings("ALL")
    public String intern(String sample) {
        if (sample == null) {
            return null;
        }
        String interned = internedStrings.get(sample);
        if (interned != null) {
            return interned;
        }
        internedStrings.put(sample, sample);
        return sample;
    }
}
