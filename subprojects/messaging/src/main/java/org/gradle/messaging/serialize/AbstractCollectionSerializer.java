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
package org.gradle.messaging.serialize;

import java.util.Collection;

public class AbstractCollectionSerializer<T> {
    protected final Serializer<T> entrySerializer;

    public AbstractCollectionSerializer(Serializer<T> entrySerializer) {
        this.entrySerializer = entrySerializer;
    }

    protected void readValues(Decoder decoder, Collection<T> values) throws Exception {
        int size = decoder.readInt();
        for (int i = 0; i < size; i++) {
            values.add(entrySerializer.read(decoder));
        }
    }

    protected void writeValues(Encoder encoder, Collection<T> value) throws Exception {
        encoder.writeInt(value.size());
        for (T t : value) {
            entrySerializer.write(encoder, t);
        }
    }
}
