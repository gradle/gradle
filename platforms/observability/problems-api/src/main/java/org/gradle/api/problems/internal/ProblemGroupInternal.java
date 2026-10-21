/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemGroup;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Internal view of a {@code ProblemGroup} implementation owned by Gradle.
 * <p>
 * Gradle-owned instances are kept as they are when problems are built and serialized; foreign subclasses of
 * {@code ProblemGroup} are copied into a Gradle-owned implementation. Internal consumers such as report and trace
 * renderers work against this type, which also exposes the description that is not part of the public API.
 */
public interface ProblemGroupInternal {

    /**
     * The name of the group, see {@code ProblemGroup#getName()}.
     */
    String getName();

    /**
     * The display name of the group, see {@code ProblemGroup#getDisplayName()}.
     */
    String getDisplayName();

    /**
     * The parent group, or {@code null} for root groups. The parent of a Gradle-owned group is always Gradle-owned:
     * {@code ProblemGroup.create} copies a foreign parent chain into Gradle-owned groups, and the problem builder does the
     * same for a foreign group. Internal code walks the hierarchy through this method.
     * <p>
     * Named differently from the public {@code ProblemGroup#getParent()} because implementations extend the public abstract
     * class and implement this interface, and the two return types are unrelated. Once the public method has been removed,
     * this one takes its name.
     */
    @Nullable
    ProblemGroupInternal getParentInternal();

    /**
     * A description of what problems in this group are about, or {@code null} if the group has none.
     * <p>
     * Predefined groups have a description; groups created through the public API do not. Descriptions are
     * surfaced to consumers by Gradle-owned renderers (reports, traces) and are not part of the public API.
     */
    @Nullable
    String getDescription();

    /**
     * Views a Gradle-owned group as {@code ProblemGroupInternal}. Every group that reaches a reported problem is Gradle-owned,
     * because the problem builder copies foreign implementations; a foreign group here is a programming error.
     */
    static ProblemGroupInternal of(ProblemGroup group) {
        if (group instanceof ProblemGroupInternal) {
            return (ProblemGroupInternal) group;
        }
        throw new IllegalStateException("Problem group '" + group.getName() + "' is not Gradle-owned (" + group.getClass().getName() + "); groups must pass through the problem builder before they are consumed");
    }

    /**
     * Structural equality shared by all implementations: two groups are equal if their names and parents are equal.
     */
    static boolean structurallyEquals(ProblemGroup self, @Nullable Object o) {
        if (self == o) {
            return true;
        }
        if (!(o instanceof ProblemGroup)) {
            return false;
        }
        ProblemGroup that = (ProblemGroup) o;
        return self.getName().equals(that.getName()) && Objects.equals(self.getParent(), that.getParent());
    }

    /**
     * Hash code consistent with {@link #structurallyEquals(ProblemGroup, Object)}.
     */
    static int structuralHashCode(ProblemGroup self) {
        return Objects.hash(self.getName(), self.getParent());
    }
}
