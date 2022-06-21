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
import org.gradle.internal.hash.Hasher;

import javax.annotation.Nullable;

public class UnknownImplementationSnapshot extends ImplementationSnapshot {

    private final UnknownReason unknownReason;

    public UnknownImplementationSnapshot(String typeName, UnknownReason unknownReason) {
        super(typeName);
        this.unknownReason = unknownReason;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        switch (unknownReason) {
            case LAMBDA:
                throw new RuntimeException("Cannot hash implementation of lambda " + getTypeName());
            case UNKNOWN_CLASSLOADER:
                throw new RuntimeException("Cannot hash implementation of class " + getTypeName() + " loaded by an unknown classloader");
        }
    }

    @Override
    protected boolean isSameSnapshot(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UnknownImplementationSnapshot that = (UnknownImplementationSnapshot) o;

        return getTypeName().equals(that.getTypeName()) && unknownReason.equals(that.unknownReason);
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
    public UnknownReason getUnknownReason() {
        return unknownReason;
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
        return getTypeName() + "@<unknown>";
    }
}
