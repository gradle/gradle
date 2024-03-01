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

public class LambdaImplementationSnapshot extends ImplementationSnapshot {

    private final HashCode classLoaderHash;

    private final String functionalInterfaceClass;
    private final String implClass;
    private final String implMethodName;
    private final String implMethodSignature;
    private final int implMethodKind;

    public LambdaImplementationSnapshot(HashCode classLoaderHash, SerializedLambda lambda) {
        this(
            lambda.getCapturingClass(),
            classLoaderHash,
            lambda.getFunctionalInterfaceClass(),
            lambda.getImplClass(),
            lambda.getImplMethodName(),
            lambda.getImplMethodSignature(),
            lambda.getImplMethodKind()
        );
    }

    public LambdaImplementationSnapshot(
        String capturingClass,
        HashCode classLoaderHash,
        String functionalInterfaceClass,
        String implClass,
        String implMethodName,
        String implMethodSignature,
        int implMethodKind
    ) {
        super(capturingClass);
        this.classLoaderHash = classLoaderHash;
        this.functionalInterfaceClass = functionalInterfaceClass;
        this.implClass = implClass;
        this.implMethodName = implMethodName;
        this.implMethodSignature = implMethodSignature;
        this.implMethodKind = implMethodKind;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString(LambdaImplementationSnapshot.class.getName());
        hasher.putString(classIdentifier);
        hasher.putHash(classLoaderHash);
        hasher.putString(functionalInterfaceClass);
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

    public String getFunctionalInterfaceClass() {
        return functionalInterfaceClass;
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

        LambdaImplementationSnapshot that = (LambdaImplementationSnapshot) o;
        return classIdentifier.equals(that.classIdentifier) &&
            classLoaderHash.equals(that.classLoaderHash) &&
            functionalInterfaceClass.equals(that.functionalInterfaceClass) &&
            implClass.equals(that.implClass) &&
            implMethodName.equals(that.implMethodName) &&
            implMethodSignature.equals(that.implMethodSignature) &&
            implMethodKind == that.implMethodKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(classIdentifier, classLoaderHash, functionalInterfaceClass, implClass, implMethodName, implMethodSignature, implMethodKind);
    }

    @Override
    public String toString() {
        return classIdentifier + "::" + implMethodName + "@" + classLoaderHash;
    }
}
