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

public class Serializers {
    public static <T> StatefulSerializer<T> stateful(final Serializer<T> serializer) {
        return new StatefulSerializerAdapter<T>(serializer);
    }

    private static class StatefulSerializerAdapter<T> implements StatefulSerializer<T> {
        private final Serializer<T> serializer;

        public StatefulSerializerAdapter(Serializer<T> serializer) {
            this.serializer = serializer;
        }

        @Override
        public ObjectReader<T> newReader(final Decoder decoder) {
            return new ObjectReader<T>() {
                @Override
                public T read() throws Exception {
                    return serializer.read(decoder);
                }
            };
        }

        @Override
        public ObjectWriter<T> newWriter(final Encoder encoder) {
            return new ObjectWriter<T>() {
                @Override
                public void write(T value) throws Exception {
                    serializer.write(encoder, value);
                }
            };
        }
    }
}
