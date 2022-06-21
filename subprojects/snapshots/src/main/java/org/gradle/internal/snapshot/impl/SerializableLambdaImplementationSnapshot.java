/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.internal.hash.Hasher;

import javax.annotation.Nullable;
import java.lang.invoke.SerializedLambda;
import java.util.Objects;

public class SerializableLambdaImplementationSnapshot extends ImplementationSnapshot {

    private final HashCode classLoaderHash;
    private final String implClass;
    private final String implMethodName;
    private final String implMethodSignature;
    private final int implMethodKind;

    public SerializableLambdaImplementationSnapshot(HashCode classLoaderHash, SerializedLambda lambda) {
        this(
            lambda.getCapturingClass(),
            classLoaderHash,
            lambda.getImplClass(),
            lambda.getImplMethodName(),
            lambda.getImplMethodSignature(),
            lambda.getImplMethodKind()
        );
    }

    public SerializableLambdaImplementationSnapshot(
        String capturingClass,
        HashCode classLoaderHash,
        String implClass,
        String implMethodName,
        String implMethodSignature,
        int implMethodKind
    ) {
        super(capturingClass);
        this.classLoaderHash = classLoaderHash;
        this.implClass = implClass;
        this.implMethodName = implMethodName;
        this.implMethodSignature = implMethodSignature;
        this.implMethodKind = implMethodKind;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString(ImplementationSnapshot.class.getName());
        hasher.putString(getTypeName());
        hasher.putHash(classLoaderHash);
        hasher.putString(implClass);
        hasher.putString(implMethodName);
        hasher.putString(implMethodSignature);
        hasher.putInt(implMethodKind);
    }

    @Nullable
    @Override
    public HashCode getClassLoaderHash() {
        return classLoaderHash;
    }

    public String getImplClass() {
        return implClass;
    }

    public String getImplMethodName() {
        return implMethodName;
    }

    public String getImplMethodSignature() {
        return implMethodSignature;
    }

    public int getImplMethodKind() {
        return implMethodKind;
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Nullable
    @Override
    public UnknownReason getUnknownReason() {
        return null;
    }

    @Override
    protected boolean isSameSnapshot(@Nullable Object o) {
        return equals(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerializableLambdaImplementationSnapshot that = (SerializableLambdaImplementationSnapshot) o;
        return implMethodKind == that.implMethodKind &&
            classLoaderHash.equals(that.classLoaderHash) &&
            implClass.equals(that.implClass) &&
            implMethodName.equals(that.implMethodName) &&
            implMethodSignature.equals(that.implMethodSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classLoaderHash, implClass, implMethodName, implMethodSignature, implMethodKind);
    }
}
