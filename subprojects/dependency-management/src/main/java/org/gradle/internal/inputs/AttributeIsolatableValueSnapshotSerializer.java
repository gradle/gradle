/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.inputs;

import org.gradle.api.attributes.Attribute;
import org.gradle.internal.Cast;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.IsolatableValueSnapshotSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;

public class AttributeIsolatableValueSnapshotSerializer implements IsolatableValueSnapshotSerializer<Attribute<?>> {

    private final Attribute<?> attribute;

    public AttributeIsolatableValueSnapshotSerializer(Attribute<?> attribute) {
        this.attribute = attribute;
    }

    @Override
    public int getSerializationTag() {
        return 100;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString(attribute.getName());
        hasher.putString(attribute.getType().getName());
    }

    @Override
    public Attribute<?> read(Decoder decoder) throws Exception {
        String name = decoder.readString();
        String typeName = decoder.readString();
        return Attribute.of(name, Class.forName(typeName)); // TODO class load
    }

    @Override
    public void write(Encoder encoder, Attribute<?> value) throws Exception {
        encoder.writeSmallInt(getSerializationTag());
        encoder.writeString(attribute.getName());
        encoder.writeString(attribute.getType().getName());
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return this;
    }

    @Nullable
    @Override
    public Attribute<?> isolate() {
        return attribute;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isInstance(attribute)) {
            return type.cast(attribute);
        }
        return null;
    }

    @Override
    public Class<Attribute<?>> getIsolatableClass() {
        return Cast.uncheckedCast(Attribute.class);
    }

    @Override
    public ValueSnapshot snapshot(@Nullable Object value, ValueSnapshotter snapshotter) {
        if (this.attribute.equals(value)) {
            return this;
        }
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (snapshot.equals(this)) {
            return this;
        }
        return snapshot;
    }
}
