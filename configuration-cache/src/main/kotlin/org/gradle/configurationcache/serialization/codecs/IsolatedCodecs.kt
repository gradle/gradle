/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.snapshot.impl.BooleanValueSnapshot
import org.gradle.internal.snapshot.impl.FileValueSnapshot
import org.gradle.internal.snapshot.impl.IntegerValueSnapshot
import org.gradle.internal.snapshot.impl.IsolatedArray
import org.gradle.internal.snapshot.impl.IsolatedEnumValueSnapshot
import org.gradle.internal.snapshot.impl.IsolatedImmutableManagedValue
import org.gradle.internal.snapshot.impl.IsolatedList
import org.gradle.internal.snapshot.impl.IsolatedManagedValue
import org.gradle.internal.snapshot.impl.IsolatedMap
import org.gradle.internal.snapshot.impl.IsolatedJavaSerializedValueSnapshot
import org.gradle.internal.snapshot.impl.IsolatedSet
import org.gradle.internal.snapshot.impl.MapEntrySnapshot
import org.gradle.internal.snapshot.impl.NullValueSnapshot
import org.gradle.internal.snapshot.impl.StringValueSnapshot
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ManagedFactoryRegistry


object NullValueSnapshotCodec : Codec<NullValueSnapshot> {
    override suspend fun WriteContext.encode(value: NullValueSnapshot) {
    }

    override suspend fun ReadContext.decode(): NullValueSnapshot {
        return NullValueSnapshot.INSTANCE
    }
}


object IsolatedEnumValueSnapshotCodec : Codec<IsolatedEnumValueSnapshot> {
    override suspend fun WriteContext.encode(value: IsolatedEnumValueSnapshot) {
        write(value.value)
    }

    override suspend fun ReadContext.decode(): IsolatedEnumValueSnapshot {
        val value = read() as Enum<*>
        return IsolatedEnumValueSnapshot(value)
    }
}


object IsolatedSetCodec : Codec<IsolatedSet> {
    override suspend fun WriteContext.encode(value: IsolatedSet) {
        write(value.elements)
    }

    override suspend fun ReadContext.decode(): IsolatedSet {
        val elements = readNonNull<ImmutableSet<Isolatable<*>>>()
        return IsolatedSet(elements)
    }
}


object IsolatedListCodec : Codec<IsolatedList> {
    override suspend fun WriteContext.encode(value: IsolatedList) {
        write(value.elements)
    }

    override suspend fun ReadContext.decode(): IsolatedList {
        val elements = readNonNull<ImmutableList<Isolatable<*>>>()
        return IsolatedList(elements)
    }
}


object IsolatedMapCodec : Codec<IsolatedMap> {
    override suspend fun WriteContext.encode(value: IsolatedMap) {
        write(value.entries)
    }

    override suspend fun ReadContext.decode(): IsolatedMap {
        val elements = readNonNull<ImmutableList<MapEntrySnapshot<Isolatable<*>>>>()
        return IsolatedMap(elements)
    }
}


object MapEntrySnapshotCodec : Codec<MapEntrySnapshot<Any>> {
    override suspend fun WriteContext.encode(value: MapEntrySnapshot<Any>) {
        write(value.key)
        write(value.value)
    }

    override suspend fun ReadContext.decode(): MapEntrySnapshot<Any> {
        val key = read() as Any
        val value = read() as Any
        return MapEntrySnapshot(key, value)
    }
}


object IsolatedArrayCodec : Codec<IsolatedArray> {
    override suspend fun WriteContext.encode(value: IsolatedArray) {
        writeClass(value.arrayType)
        write(value.elements)
    }

    override suspend fun ReadContext.decode(): IsolatedArray {
        val arrayType = readClass()
        val elements = readNonNull<ImmutableList<Isolatable<*>>>()
        return IsolatedArray(elements, arrayType)
    }
}


object StringValueSnapshotCodec : Codec<StringValueSnapshot> {
    override suspend fun WriteContext.encode(value: StringValueSnapshot) {
        writeString(value.value)
    }

    override suspend fun ReadContext.decode(): StringValueSnapshot {
        val value = readString()
        return StringValueSnapshot(value)
    }
}


object IntegerValueSnapshotCodec : Codec<IntegerValueSnapshot> {
    override suspend fun WriteContext.encode(value: IntegerValueSnapshot) {
        writeInt(value.value)
    }

    override suspend fun ReadContext.decode(): IntegerValueSnapshot {
        val value = readInt()
        return IntegerValueSnapshot(value)
    }
}


object FileValueSnapshotCodec : Codec<FileValueSnapshot> {
    override suspend fun WriteContext.encode(value: FileValueSnapshot) {
        writeString(value.value)
    }

    override suspend fun ReadContext.decode(): FileValueSnapshot {
        val value = readString()
        return FileValueSnapshot(value)
    }
}


object BooleanValueSnapshotCodec : Codec<BooleanValueSnapshot> {
    override suspend fun WriteContext.encode(value: BooleanValueSnapshot) {
        writeBoolean(value.value)
    }

    override suspend fun ReadContext.decode(): BooleanValueSnapshot {
        val value = readBoolean()
        return BooleanValueSnapshot(value)
    }
}


class IsolatedManagedValueCodec(private val managedFactory: ManagedFactoryRegistry) : Codec<IsolatedManagedValue> {
    override suspend fun WriteContext.encode(value: IsolatedManagedValue) {
        writeClass(value.targetType)
        writeSmallInt(value.factoryId)
        write(value.state)
    }

    override suspend fun ReadContext.decode(): IsolatedManagedValue {
        val targetType = readClass()
        val factoryId = readSmallInt()
        val state = readNonNull<Isolatable<Any>>()
        return IsolatedManagedValue(targetType, managedFactory.lookup(factoryId), state)
    }
}


class IsolatedImmutableManagedValueCodec(private val managedFactory: ManagedFactoryRegistry) : Codec<IsolatedImmutableManagedValue> {
    override suspend fun WriteContext.encode(value: IsolatedImmutableManagedValue) {
        write(value.value)
    }

    override suspend fun ReadContext.decode(): IsolatedImmutableManagedValue {
        val state = read() as Managed
        return IsolatedImmutableManagedValue(state, managedFactory)
    }
}


object IsolatedJavaSerializedValueSnapshotCodec : Codec<IsolatedJavaSerializedValueSnapshot> {
    override suspend fun WriteContext.encode(value: IsolatedJavaSerializedValueSnapshot) {
        write(value.implementationHash)
        writeClass(value.originalClass)
        writeBinary(value.value)
    }

    override suspend fun ReadContext.decode(): IsolatedJavaSerializedValueSnapshot? {
        val implementationHash = read() as HashCode?
        val originalType = readClass()
        val binary = readBinary()
        return IsolatedJavaSerializedValueSnapshot(implementationHash, binary, originalType)
    }
}
