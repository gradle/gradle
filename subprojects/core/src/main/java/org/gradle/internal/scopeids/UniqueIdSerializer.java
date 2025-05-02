/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scopeids;

import org.gradle.internal.id.UniqueId;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

class UniqueIdSerializer implements Serializer<UniqueId> {

    public static final Serializer<UniqueId> INSTANCE = new UniqueIdSerializer();

    private UniqueIdSerializer() {
    }

    @Override
    public UniqueId read(Decoder decoder) throws Exception {
        String string = decoder.readString();
        return UniqueId.from(string);
    }

    @Override
    public void write(Encoder encoder, UniqueId value) throws Exception {
        encoder.writeString(value.asString());
    }

}
