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

package org.gradle.internal.serialize;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

import java.io.EOFException;

public class IntSetSerializer implements Serializer<IntSet> {
    public static final IntSetSerializer INSTANCE = new IntSetSerializer();

    private IntSetSerializer() {
    }

    @Override
    public IntSet read(Decoder decoder) throws EOFException, Exception {
        int size = decoder.readInt();
        if (size == 0) {
            return IntSets.EMPTY_SET;
        }
        IntSet result = new IntOpenHashSet(size);
        for (int i = 0; i < size; i++) {
            result.add(decoder.readInt());
        }
        return result;
    }

    @Override
    public void write(Encoder encoder, IntSet value) throws Exception {
        encoder.writeInt(value.size());
        IntIterator iterator = value.iterator();
        while(iterator.hasNext()) {
            encoder.writeInt(iterator.nextInt());
        }
    }
}
