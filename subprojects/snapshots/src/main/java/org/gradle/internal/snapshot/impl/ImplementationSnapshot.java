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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public abstract class ImplementationSnapshot implements ValueSnapshot {
    private static final String GENERATED_LAMBDA_CLASS_SUFFIX = "$$Lambda$";

    private final String typeName;

    public static ImplementationSnapshot of(Class<?> type, ClassLoaderHierarchyHasher classLoaderHasher) {
        String className = type.getName();
        return of(className, classLoaderHasher.getClassLoaderHash(type.getClassLoader()), type.isSynthetic() && isLambdaClassName(className));
    }

    public static ImplementationSnapshot of(String className, @Nullable HashCode classLoaderHash) {
        return of(className, classLoaderHash, isLambdaClassName(className));
    }

    private static ImplementationSnapshot of(String typeName, @Nullable HashCode classLoaderHash, boolean lambda) {
        if (classLoaderHash == null) {
            return new UnknownClassloaderImplementationSnapshot(typeName);
        }
        if (lambda) {
            return new LambdaImplementationSnapshot(typeName);
        }
        return new DefaultImplementationSnapshot(typeName, classLoaderHash);
    }

    private static boolean isLambdaClassName(String className) {
        return className.contains(GENERATED_LAMBDA_CLASS_SUFFIX);
    }

    protected ImplementationSnapshot(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    @Nullable
    public abstract HashCode getClassLoaderHash();

    public abstract boolean isUnknown();

    @Nullable
    public abstract String getUnknownReason();

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot other = snapshotter.snapshot(value);
        if (this.isSameSnapshot(other)) {
            return this;
        }
        return other;
    }

    protected abstract boolean isSameSnapshot(Object o);

    private static class DefaultImplementationSnapshot extends ImplementationSnapshot {
        private final HashCode classLoaderHash;

        public DefaultImplementationSnapshot(String typeName, HashCode classLoaderHash) {
            super(typeName);
            this.classLoaderHash = classLoaderHash;
        }

        @Override
        public void appendToHasher(Hasher hasher) {
            hasher.putString(ImplementationSnapshot.class.getName());
            hasher.putString(getTypeName());
            hasher.putHash(classLoaderHash);
        }

        @Override
        protected boolean isSameSnapshot(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DefaultImplementationSnapshot that = (DefaultImplementationSnapshot) o;

            if (!getTypeName().equals(that.getTypeName())) {
                return false;
            }
            return classLoaderHash.equals(that.classLoaderHash);
        }

        @Override
        public HashCode getClassLoaderHash() {
            return classLoaderHash;
        }

        @Override
        public boolean isUnknown() {
            return false;
        }

        @Override
        @Nullable
        public String getUnknownReason() {
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DefaultImplementationSnapshot that = (DefaultImplementationSnapshot) o;
            if (this == o) {
                return true;
            }


            if (!getTypeName().equals(that.getTypeName())) {
                return false;
            }
            return classLoaderHash.equals(that.classLoaderHash);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + classLoaderHash.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return getTypeName() + "@" + classLoaderHash;
        }
    }

    private static class LambdaImplementationSnapshot extends ImplementationSnapshot {

        public LambdaImplementationSnapshot(String typeName) {
            super(typeName);
        }

        @Override
        public void appendToHasher(Hasher hasher) {
            hasher.markAsInvalid(getUnknownReason());
        }

        @Override
        protected boolean isSameSnapshot(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LambdaImplementationSnapshot that = (LambdaImplementationSnapshot) o;

            return getTypeName().equals(that.getTypeName());
        }

        @Override
        public HashCode getClassLoaderHash() {
            return null;
        }

        @Override
        public boolean isUnknown() {
            return true;
        }

        @Override
        @Nullable
        public String getUnknownReason() {
            return "was implemented by the Java lambda '" + getTypeName() + "'. Using Java lambdas is not supported, use an (anonymous) inner class instead.";
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public int hashCode() {
            return getTypeName().hashCode();
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private static class UnknownClassloaderImplementationSnapshot extends ImplementationSnapshot {

        public UnknownClassloaderImplementationSnapshot(String typeName) {
            super(typeName);
        }

        @Override
        public void appendToHasher(Hasher hasher) {
            hasher.markAsInvalid(getUnknownReason());
        }

        @Override
        protected boolean isSameSnapshot(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            UnknownClassloaderImplementationSnapshot that = (UnknownClassloaderImplementationSnapshot) o;

            return getTypeName().equals(that.getTypeName());
        }

        @Override
        public HashCode getClassLoaderHash() {
            return null;
        }

        @Override
        public boolean isUnknown() {
            return true;
        }

        @Override
        @Nullable
        public String getUnknownReason() {
            return "was loaded with an unknown classloader (class '" + getTypeName() + "').";
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public int hashCode() {
            return getTypeName().hashCode();
        }

        @Override
        public String toString() {
            return getTypeName() + "@" + "<Unknown classloader>";
        }
    }

    public static class SerializerImpl implements Serializer<ImplementationSnapshot> {
        
        private enum ImplementationSnapshotSerializer implements Serializer<ImplementationSnapshot> {
            DEFAULT {
                @Override
                protected ImplementationSnapshot doRead(String typeName, Decoder decoder) throws Exception {
                    HashCode classLoaderHash = hashCodeSerializer.read(decoder);
                    return new DefaultImplementationSnapshot(typeName, classLoaderHash);
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
            ImplementationSnapshotSerializer serializer = ImplementationSnapshotSerializer.values()[decoder.readSmallInt()];
            return serializer.read(decoder);
        }

        @Override
        public void write(Encoder encoder, ImplementationSnapshot implementationSnapshot) throws Exception {
            ImplementationSnapshotSerializer serializer = determineSerializer(implementationSnapshot);
            encoder.writeSmallInt(serializer.ordinal());
            serializer.write(encoder, implementationSnapshot);
        }

        private ImplementationSnapshotSerializer determineSerializer(ImplementationSnapshot implementationSnapshot) {
            if (implementationSnapshot instanceof DefaultImplementationSnapshot) {
                return ImplementationSnapshotSerializer.DEFAULT;
            }
            if (implementationSnapshot instanceof UnknownClassloaderImplementationSnapshot) {
                return ImplementationSnapshotSerializer.UNKNOWN_CLASSLOADER;
            }
            if (implementationSnapshot instanceof LambdaImplementationSnapshot) {
                return ImplementationSnapshotSerializer.LAMBDA;
            }
            throw new IllegalArgumentException("Unknown implementation snapshot type: " + implementationSnapshot.getClass().getName());
        }
    }
}
