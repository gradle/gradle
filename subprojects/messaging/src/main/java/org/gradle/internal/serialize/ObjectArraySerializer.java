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

package org.gradle.internal.serialize;

public class ObjectArraySerializer implements Serializer<Object[]> {
    private final Serializer<Object> elementSerializer;

    public ObjectArraySerializer(Serializer<Object> elementSerializer) {
        this.elementSerializer = elementSerializer;
    }

    @Override
    public void write(Encoder encoder, Object[] value) throws Exception {
        encoder.writeSmallInt(value.length);
        for (int i = 0; i < value.length; i++) {
            elementSerializer.write(encoder, value[i]);
        }
    }

    @Override
    public Object[] read(Decoder decoder) throws Exception {
        int len = decoder.readSmallInt();
        Object[] result = new Object[len];
        for (int i = 0; i < result.length; i++) {
            result[i] = elementSerializer.read(decoder);
        }
        return result;
    }
}
