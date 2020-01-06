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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

public class IsolatedSet extends AbstractSetSnapshot<Isolatable<?>> implements Isolatable<Set<Object>> {
    public IsolatedSet(ImmutableSet<Isolatable<?>> elements) {
        super(elements);
    }

    @Override
    public ValueSnapshot asSnapshot() {
        ImmutableSet.Builder<ValueSnapshot> builder = ImmutableSet.builderWithExpectedSize(elements.size());
        for (Isolatable<?> element : elements) {
            builder.add(element.asSnapshot());
        }
        return new SetValueSnapshot(builder.build());
    }

    @Override
    public Set<Object> isolate() {
        Set<Object> set = new LinkedHashSet<>(elements.size());
        for (Isolatable<?> element : elements) {
            set.add(element.isolate());
        }
        return set;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }
}
