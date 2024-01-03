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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class IsolatedList extends AbstractListSnapshot<Isolatable<?>> implements Isolatable<List<Object>> {
    public static final IsolatedList EMPTY = new IsolatedList(ImmutableList.of());

    public IsolatedList(ImmutableList<Isolatable<?>> elements) {
        super(elements);
    }

    @Override
    public ValueSnapshot asSnapshot() {
        if (elements.isEmpty()) {
            return ListValueSnapshot.EMPTY;
        }
        ImmutableList.Builder<ValueSnapshot> builder = ImmutableList.builderWithExpectedSize(elements.size());
        for (Isolatable<?> element : elements) {
            builder.add(element.asSnapshot());
        }
        return new ListValueSnapshot(builder.build());
    }

    @Override
    public List<Object> isolate() {
        List<Object> list = new ArrayList<Object>(elements.size());
        for (Isolatable<?> element : elements) {
            list.add(element.isolate());
        }
        return list;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }
}
