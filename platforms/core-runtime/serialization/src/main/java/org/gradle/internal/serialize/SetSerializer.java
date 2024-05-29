/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.serialize;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Set;

public class SetSerializer<T> extends AbstractCollectionSerializer<T, Set<T>> implements Serializer<Set<T>> {

    private final boolean linkedHashSet;

    public SetSerializer(Serializer<T> entrySerializer) {
        this(entrySerializer, true);
    }

    public SetSerializer(Serializer<T> entrySerializer, boolean linkedHashSet) {
        super(entrySerializer);
        this.linkedHashSet = linkedHashSet;
    }

    @Override
    protected Set<T> createCollection(int size) {
        if (size == 0) {
            return Collections.emptySet();
        }
        return linkedHashSet ? Sets.<T>newLinkedHashSetWithExpectedSize(size) : Sets.<T>newHashSetWithExpectedSize(size);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        SetSerializer<?> rhs = (SetSerializer<?>) obj;
        return linkedHashSet == rhs.linkedHashSet;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), linkedHashSet);
    }

}
