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

package org.gradle.problems.internal.rendering;

import org.gradle.api.problems.ProblemGroup;

/**
 * Identifies the predefined {@code Compilation > Java} problem group, which the consumers that treat Java compilation
 * problems specially need to recognize.
 * <p>
 * The predefined group instances are reached through the {@code Problems} service. The rendering code has no such
 * service, and it also runs inside the compiler worker, so the group is identified structurally by the names of its
 * chain. Group identity is structural, so this is the same comparison as {@code ProblemGroup.equals} against the
 * predefined instance.
 */
public final class JavaCompilationProblems {

    private static final String COMPILATION_GROUP_NAME = "Compilation";
    private static final String JAVA_GROUP_NAME = "Java";

    private JavaCompilationProblems() {
    }

    /**
     * Whether the given group is the predefined {@code Compilation > Java} group itself.
     */
    public static boolean isJavaCompilationGroup(ProblemGroup group) {
        ProblemGroup parent = group.getParent();
        return JAVA_GROUP_NAME.equals(group.getName())
            && parent != null
            && COMPILATION_GROUP_NAME.equals(parent.getName())
            && parent.getParent() == null;
    }

    /**
     * Whether the given group is the predefined {@code Compilation > Java} group or one of its sub-groups.
     */
    public static boolean isInJavaCompilationGroup(ProblemGroup group) {
        for (ProblemGroup current = group; current != null; current = current.getParent()) {
            if (isJavaCompilationGroup(current)) {
                return true;
            }
        }
        return false;
    }
}
