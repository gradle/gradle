/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.logging.serializer;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.time.Timestamp;

public class TimestampSerializer implements Serializer<Timestamp> {
    @Override
    public Timestamp read(Decoder decoder) throws Exception {
        long epochMs = decoder.readLong();
        long nanos = decoder.readLong();
        return Timestamp.ofMillis(epochMs, nanos);
    }

    @Override
    public void write(Encoder encoder, Timestamp value) throws Exception {
        encoder.writeLong(value.getTimeMs());
        encoder.writeLong(value.getNanosOfMillis());
    }
}
