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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class SetSerializer<T> extends AbstractCollectionSerializer<T> implements Serializer<Set<T>> {

    private final boolean linkedHashSet;

    public SetSerializer(Serializer<T> entrySerializer) {
        this(entrySerializer, true);
    }

    public SetSerializer(Serializer<T> entrySerializer, boolean linkedHashSet) {
        super(entrySerializer);
        this.linkedHashSet = linkedHashSet;
    }

    public Set<T> read(Decoder decoder) throws Exception {
        Set<T> values = linkedHashSet? new LinkedHashSet<T>() : new HashSet<T>();
        readValues(decoder, values);
        return values;
    }

    public void write(Encoder encoder, Set<T> value) throws Exception {
        writeValues(encoder, value);
    }

}
