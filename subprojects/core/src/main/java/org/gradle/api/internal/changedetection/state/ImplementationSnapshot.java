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

package org.gradle.api.internal.changedetection.state;

import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.SingleMessageLogger;

import javax.annotation.Nullable;

/**
 * Identifies a type in a classloader hierarchy. The type is identified by its name,
 * the classloader hierarchy by its hash code.
 */
public class ImplementationSnapshot implements ValueSnapshot {
    private static final String GENERATED_LAMBDA_CLASS_SUFFIX = "$$Lambda$";

    private final String typeName;
    private final HashCode classLoaderHash;

    public static ImplementationSnapshot of(Class<?> type, ClassLoaderHierarchyHasher classLoaderHasher) {
        return of(determineImplementationName(type.getName(), type.isSynthetic()), classLoaderHasher.getClassLoaderHash(type.getClassLoader()));
    }

    public static ImplementationSnapshot of(String typeName, @Nullable HashCode classLoaderHash) {
        return new ImplementationSnapshot(typeName, classLoaderHash);
    }

    public static ImplementationSnapshot withDeterministicClassName(String className, @Nullable HashCode classLoaderHash) {
        return of(determineImplementationName(className, true), classLoaderHash);
    }

    private static String determineImplementationName(String className, boolean mayBeLambda) {
        if (mayBeLambda && className.contains(GENERATED_LAMBDA_CLASS_SUFFIX)) {
            SingleMessageLogger.nagUserWith(
                "Java lambda is used as an input.",
                "Gradle can only track the lambda used in your code with some loss of precision that may lead to some changes going undetected.",
                "Use an anonymous inner class instead.",
                ""
            );
            int index = className.lastIndexOf(GENERATED_LAMBDA_CLASS_SUFFIX);
            return className.substring(0, index + GENERATED_LAMBDA_CLASS_SUFFIX.length());
        } else {
            return className;
        }
    }

    private ImplementationSnapshot(String typeName, @Nullable HashCode classLoaderHash) {
        this.typeName = typeName;
        this.classLoaderHash = classLoaderHash;
    }

    public String getTypeName() {
        return typeName;
    }

    public HashCode getClassLoaderHash() {
        if (classLoaderHash == null) {
            throw new NullPointerException("classLoaderHash");
        }
        return classLoaderHash;
    }

    public boolean hasUnknownClassLoader() {
        return classLoaderHash == null;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        if (classLoaderHash == null) {
            hasher.markAsInvalid();
        } else {
            hasher.putString(ImplementationSnapshot.class.getName());
            hasher.putString(typeName);
            hasher.putHash(classLoaderHash);
        }
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot other = snapshotter.snapshot(value);
        if (this.isSameSnapshot(other)) {
            return this;
        }
        return other;
    }

    private boolean isSameSnapshot(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImplementationSnapshot that = (ImplementationSnapshot) o;

        if (!typeName.equals(that.typeName)) {
            return false;
        }
        return classLoaderHash != null ? classLoaderHash.equals(that.classLoaderHash) : that.classLoaderHash == null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImplementationSnapshot that = (ImplementationSnapshot) o;
        if (classLoaderHash == null || that.classLoaderHash == null) {
            return false;
        }
        if (this == o) {
            return true;
        }


        if (!typeName.equals(that.typeName)) {
            return false;
        }
        return classLoaderHash.equals(that.classLoaderHash);
    }

    @Override
    public int hashCode() {
        int result = typeName.hashCode();
        result = 31 * result + (classLoaderHash != null ? classLoaderHash.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return typeName + "@" + classLoaderHash;
    }
}
