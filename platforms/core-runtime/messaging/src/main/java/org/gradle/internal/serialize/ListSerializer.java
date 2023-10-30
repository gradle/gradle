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

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

public class ListSerializer<T> extends AbstractCollectionSerializer<T, List<T>> implements Serializer<List<T>> {

    public ListSerializer(Serializer<T> entrySerializer) {
        super(entrySerializer);
    }

    @Override
    protected List<T> createCollection(int size) {
        if (size == 0) {
            return Collections.emptyList();
        }
        return Lists.newArrayListWithCapacity(size);
    }
}
