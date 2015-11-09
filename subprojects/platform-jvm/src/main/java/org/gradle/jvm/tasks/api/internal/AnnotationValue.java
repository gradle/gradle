/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.tasks.api.internal;

public abstract class AnnotationValue<V> extends Member implements Comparable<AnnotationValue<?>> {

    private final V value;

    public AnnotationValue(String name, V value) {
        super(name);
        this.value = value;
    }

    public V getValue() {
        return value;
    }

    @Override
    public int compareTo(AnnotationValue<?> o) {
        return super.compare(o).result();
    }
}
