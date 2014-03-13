/*
 * Copyright 2014 the original author or authors.
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

import java.io.File;

public class BaseSerializerFactory {
    public static final Serializer<String> STRING_SERIALIZER = new StringSerializer();
    public static final Serializer LONG_SERIALIZER = new LongSerializer();
    public static final Serializer FILE_SERIALIZER = new FileSerializer();

    public <T> Serializer<T> getSerializerFor(Class<T> type) {
        if (type.equals(String.class)) {
            @SuppressWarnings("unchecked")
            Serializer<T> stringSerializer = (Serializer<T>) STRING_SERIALIZER;
            return stringSerializer;
        }
        if (type.equals(Long.class)) {
            return LONG_SERIALIZER;
        }
        if (type.equals(File.class)) {
            return FILE_SERIALIZER;
        }
        return new DefaultSerializer<T>(type.getClassLoader());
    }

    private static class LongSerializer implements Serializer<Long> {
        public Long read(Decoder decoder) throws Exception {
            return decoder.readLong();
        }

        public void write(Encoder encoder, Long value) throws Exception {
            encoder.writeLong(value);
        }
    }

    private static class StringSerializer implements Serializer<String> {
        public String read(Decoder decoder) throws Exception {
            return decoder.readString();
        }

        public void write(Encoder encoder, String value) throws Exception {
            encoder.writeString(value);
        }
    }

    private static class FileSerializer implements Serializer<File> {
        public File read(Decoder decoder) throws Exception {
            return new File(decoder.readString());
        }

        public void write(Encoder encoder, File value) throws Exception {
            encoder.writeString(value.getPath());
        }
    }
}
