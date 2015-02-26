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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.GradleException;
import org.gradle.internal.serialize.*;

import java.util.Map;

import static java.lang.String.format;

class InputPropertiesSerializer implements Serializer<Map<String, Object>> {

    private final MapSerializer<String, Object> serializer;

    InputPropertiesSerializer(ClassLoader classloader) {
        this.serializer = new MapSerializer<String, Object>(BaseSerializerFactory.STRING_SERIALIZER, new DefaultSerializer<Object>(classloader));
    }

    public Map<String, Object> read(Decoder decoder) throws Exception {
        return serializer.read(decoder);
    }

    public void write(Encoder encoder, Map<String, Object> properties) throws Exception {
        try {
            serializer.write(encoder, properties);
        } catch (MapSerializer.EntrySerializationException e) {
            throw new GradleException(format("Unable to store task input properties. Property '%s' with value '%s' cannot be serialized.", e.getKey(), e.getValue()), e);
        }
    }
}
