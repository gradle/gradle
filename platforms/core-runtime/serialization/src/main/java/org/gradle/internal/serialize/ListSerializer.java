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

import com.google.common.collect.ImmutableList;

import java.util.List;

public class ListSerializer<T> extends AbstractSerializer<List<T>> {

    private final Serializer<T> entrySerializer;

    public ListSerializer(Serializer<T> entrySerializer) {
        this.entrySerializer = entrySerializer;
    }

    @Override
    public ImmutableList<T> read(Decoder decoder) throws Exception {
        int size = decoder.readInt();
        ImmutableList.Builder<T> values = ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            values.add(entrySerializer.read(decoder));
        }
        return values.build();
    }

    @Override
    public void write(Encoder encoder, List<T> value) throws Exception {
        encoder.writeInt(value.size());
        for (T t : value) {
            entrySerializer.write(encoder, t);
        }
    }

}
