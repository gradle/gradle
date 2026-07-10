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
        CLASS {
            @Override
            protected ImplementationSnapshot readAdditionalData(String classIdentifier, Decoder decoder) throws Exception {
                HashCode classLoaderHash = hashCodeSerializer.read(decoder);
                return new ClassImplementationSnapshot(classIdentifier, classLoaderHash);
            }

            @Override
            public void writeAdditionalData(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
                hashCodeSerializer.write(encoder, implementationSnapshot.getClassLoaderHash());
            }
        },
        LAMBDA {
            @Override
            protected ImplementationSnapshot readAdditionalData(String classIdentifier, Decoder decoder) throws Exception {
                HashCode classLoaderHash = hashCodeSerializer.read(decoder);
                String functionalInterfaceClass = decoder.readString();
                String implClass = decoder.readString();
                String implMethodName = decoder.readString();
                String implMethodSignature = decoder.readString();
                int implMethodKind = decoder.readSmallInt();
                return new LambdaImplementationSnapshot(
                    classIdentifier,
                    classLoaderHash,
                    functionalInterfaceClass,
                    implClass,
                    implMethodName,
                    implMethodSignature,
                    implMethodKind
                );
            }

            @Override
            protected void writeAdditionalData(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
                LambdaImplementationSnapshot serLambda = (LambdaImplementationSnapshot) implementationSnapshot;
                hashCodeSerializer.write(encoder, serLambda.getClassLoaderHash());
                encoder.writeString(serLambda.getFunctionalInterfaceClass());
                encoder.writeString(serLambda.getImplClass());
                encoder.writeString(serLambda.getImplMethodName());
                encoder.writeString(serLambda.getImplMethodSignature());
                encoder.writeSmallInt(serLambda.getImplMethodKind());
            }
        },
        UNKNOWN {
            @Override
            protected ImplementationSnapshot readAdditionalData(String classIdentifier, Decoder decoder) throws Exception {
                UnknownImplementationSnapshot.UnknownReason unknownReason = UnknownImplementationSnapshot.UnknownReason.values()[decoder.readSmallInt()];
                return new UnknownImplementationSnapshot(classIdentifier, unknownReason);
            }

            @Override
            protected void writeAdditionalData(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
                UnknownImplementationSnapshot unknownImplSnapshot = (UnknownImplementationSnapshot) implementationSnapshot;
                encoder.writeSmallInt(unknownImplSnapshot.getUnknownReason().ordinal());
            }
        };

        @Override
        public void write(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
            encoder.writeString(implementationSnapshot.getClassIdentifier());
            writeAdditionalData(encoder, implementationSnapshot);
        }

        @Override
        public ImplementationSnapshot read(Decoder decoder) throws Exception {
            String classIdentifier = decoder.readString();
            return readAdditionalData(classIdentifier, decoder);
        }

        protected final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

        protected abstract ImplementationSnapshot readAdditionalData(String classIdentifier, Decoder decoder) throws Exception;

        protected abstract void writeAdditionalData(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception;
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
        if (implementationSnapshot instanceof ClassImplementationSnapshot) {
            return Impl.CLASS;
        }
        if (implementationSnapshot instanceof LambdaImplementationSnapshot) {
            return Impl.LAMBDA;
        }
        if (implementationSnapshot instanceof UnknownImplementationSnapshot) {
            return Impl.UNKNOWN;
        }
        throw new IllegalArgumentException("Unexpected implementation snapshot type: " + implementationSnapshot.getClass().getName());
    }
}
