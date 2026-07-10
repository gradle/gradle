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

import com.google.common.base.Objects;

import java.io.EOFException;
import java.util.Collection;

public abstract class AbstractCollectionSerializer<T, C extends Collection<T>> implements Serializer<C> {
    protected final Serializer<T> entrySerializer;

    public AbstractCollectionSerializer(Serializer<T> entrySerializer) {
        this.entrySerializer = entrySerializer;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        AbstractCollectionSerializer<?, ?> rhs = (AbstractCollectionSerializer<?, ?>) obj;
        return Objects.equal(entrySerializer, rhs.entrySerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass(), entrySerializer);
    }

    protected abstract C createCollection(int size);

    @Override
    public C read(Decoder decoder) throws EOFException, Exception {
        int size = decoder.readInt();
        C values = createCollection(size);
        for (int i = 0; i < size; i++) {
            values.add(entrySerializer.read(decoder));
        }
        return values;
    }

    @Override
    public void write(Encoder encoder, C value) throws Exception {
        encoder.writeInt(value.size());
        for (T t : value) {
            entrySerializer.write(encoder, t);
        }
    }

}
