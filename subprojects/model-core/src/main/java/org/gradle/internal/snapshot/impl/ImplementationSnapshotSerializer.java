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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

public class ImplementationSnapshotSerializer implements Serializer<ImplementationSnapshot> {

    private enum Impl implements Serializer<ImplementationSnapshot> {
        DEFAULT {
            @Override
            protected ImplementationSnapshot doRead(String typeName, Decoder decoder) throws Exception {
                HashCode classLoaderHash = hashCodeSerializer.read(decoder);
                return new KnownImplementationSnapshot(typeName, classLoaderHash);
            }

            @Override
            public void writeAdditionalData(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
                hashCodeSerializer.write(encoder, implementationSnapshot.getClassLoaderHash());
            }
        },
        UNKNOWN_CLASSLOADER {
            @Override
            protected ImplementationSnapshot doRead(String typeName, Decoder decoder) {
                return new UnknownClassloaderImplementationSnapshot(typeName);
            }
        },
        LAMBDA {
            @Override
            protected ImplementationSnapshot doRead(String typeName, Decoder decoder) {
                return new LambdaImplementationSnapshot(typeName);
            }
        };

        @Override
        public void write(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
            encoder.writeString(implementationSnapshot.getTypeName());
            writeAdditionalData(encoder, implementationSnapshot);
        }

        @Override
        public ImplementationSnapshot read(Decoder decoder) throws Exception {
            String typeName = decoder.readString();
            return doRead(typeName, decoder);
        }

        protected final Serializer<HashCode> hashCodeSerializer = new HashCodeSerializer();

        protected abstract ImplementationSnapshot doRead(String typeName, Decoder decoder) throws Exception;

        protected void writeAdditionalData(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
        }
    }

    @Override
    public ImplementationSnapshot read(Decoder decoder) throws Exception {
        Impl serializer = Impl.values()[decoder.readSmallInt()];
        return serializer.read(decoder);
    }

    @Override
    public void write(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
        Impl serializer = determineSerializer(implementationSnapshot);
        encoder.writeSmallInt(serializer.ordinal());
        serializer.write(encoder, implementationSnapshot);
    }

    private static Impl determineSerializer(ImplementationSnapshot implementationSnapshot) {
        if (implementationSnapshot instanceof KnownImplementationSnapshot) {
            return Impl.DEFAULT;
        }
        if (implementationSnapshot instanceof UnknownClassloaderImplementationSnapshot) {
            return Impl.UNKNOWN_CLASSLOADER;
        }
        if (implementationSnapshot instanceof LambdaImplementationSnapshot) {
            return Impl.LAMBDA;
        }
        throw new IllegalArgumentException("Unknown implementation snapshot type: " + implementationSnapshot.getClass().getName());
    }
}
