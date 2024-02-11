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
import java.util.Objects;

public class UnknownImplementationSnapshot extends ImplementationSnapshot {

    public enum UnknownReason {
        UNKNOWN_CLASSLOADER(
            "was loaded with an unknown classloader (class '%s').",
            "Gradle cannot track the implementation for classes loaded with an unknown classloader.",
            "Load your class by using one of Gradle's built-in ways."
        ),
        UNTRACKED_LAMBDA(
            "was implemented by the Java lambda '%s'.",
            "Using Java lambdas is not supported as task inputs.",
            "Use an (anonymous inner) class instead."
        );

        private final String descriptionTemplate;
        private final String reason;
        private final String solution;

        UnknownReason(String descriptionTemplate, String reason, String solution) {
            this.descriptionTemplate = descriptionTemplate;
            this.reason = reason;
            this.solution = solution;
        }
    }

    private final UnknownReason unknownReason;

    public UnknownImplementationSnapshot(String typeName, UnknownReason unknownReason) {
        super(typeName);
        this.unknownReason = unknownReason;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        throw new UnsupportedOperationException("Cannot hash an unknown implementation " + this);
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

        return classIdentifier.equals(that.classIdentifier) && unknownReason.equals(that.unknownReason);
    }

    @Override
    public HashCode getClassLoaderHash() {
        return null;
    }

    public UnknownReason getUnknownReason() {
        return unknownReason;
    }

    public String getProblemDescription() {
        return String.format(unknownReason.descriptionTemplate, classIdentifier);
    }

    public String getReasonDescription() {
        return unknownReason.reason;
    }

    public String getSolutionDescription() {
        return unknownReason.solution;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(classIdentifier, unknownReason);
    }

    @Override
    public String toString() {
        return classIdentifier + "@<" + unknownReason.name() + ">";
    }
}
