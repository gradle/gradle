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
import java.lang.reflect.Array;

public class IsolatedArray extends AbstractArraySnapshot<Isolatable<?>> implements Isolatable<Object[]> {
    public static final IsolatedArray EMPTY = empty(Object.class);
    private final Class<?> arrayType;

    public IsolatedArray(ImmutableList<Isolatable<?>> elements, Class<?> arrayType) {
        super(elements);
        this.arrayType = arrayType;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        if (elements.isEmpty()) {
            return ArrayValueSnapshot.EMPTY;
        }
        ImmutableList.Builder<ValueSnapshot> builder = ImmutableList.builderWithExpectedSize(elements.size());
        for (Isolatable<?> element : elements) {
            builder.add(element.asSnapshot());
        }
        return new ArrayValueSnapshot(builder.build());
    }

    @Override
    public Object[] isolate() {
        Object[] toReturn = (Object[]) Array.newInstance(arrayType, elements.size());
        for (int i = 0; i < elements.size(); i++) {
            Isolatable<?> element = elements.get(i);
            toReturn[i] = element.isolate();
        }
        return toReturn;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }

    public Class<?> getArrayType() {
        return arrayType;
    }

    public static IsolatedArray empty(Class<?> arrayType) {
        return new IsolatedArray(ImmutableList.of(), arrayType);
    }
}
