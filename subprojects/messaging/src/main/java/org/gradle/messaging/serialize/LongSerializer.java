/*
 * Copyright 2011 the original author or authors.
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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LongSerializer extends DataStreamBackedSerializer<Long> {

    @Override
    public Long read(DataInput dataInput) throws Exception {
        return dataInput.readLong();
    }

    @Override
    public void write(DataOutput dataOutput, Long value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("This serializer does not serialize null values.");
        }
        dataOutput.writeLong(value);
    }
}
