/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.SortedSet;

public class AnnotationMember extends Member implements Comparable<AnnotationMember> {

    private final SortedSet<AnnotationValue<?>> values = Sets.newTreeSet();
    private final boolean visible;

    public AnnotationMember(String name, boolean visible) {
        super(name);
        this.visible = visible;
    }

    public SortedSet<AnnotationValue<?>> getValues() {
        return ImmutableSortedSet.copyOf(values);
    }

    public void addValue(AnnotationValue<?> value) {
        values.add(value);
    }

    public void addValues(Collection<AnnotationValue<?>> values) {
        this.values.addAll(values);
    }

    public boolean isVisible() {
        return visible;
    }

    protected ComparisonChain compare(AnnotationMember o) {
        return super.compare(o)
            .compare(visible, o.visible);
    }

    @Override
    public int compareTo(AnnotationMember o) {
        return compare(o).result();
    }
}
