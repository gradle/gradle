/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.groovy.support;

import org.jspecify.annotations.Nullable;

/**
 * This class provides catch-all support for {@code <OP>=} overloading.
 * <p>
 * <b>This is a hidden public API</b>. Compiling Groovy code that depends on Gradle API may end up emitting references to methods of this class.
 */
@SuppressWarnings("unused") // registered as Groovy extension in ExtensionModule
public final class CompoundAssignmentExtensions {
    private CompoundAssignmentExtensions() {}

    /**
     * No-op implementation, returns the argument.
     * This method is called when the compound assignment expression should use the default Groovy logic.
     * Classes must provide their own extension method with the exact signature to participate in the custom overloading protocol.
     * <p>
     * The AST transformer knows the name of this method.
     *
     * @param lhs the original argument
     * @param <T> the type of the argument, used to please the static type checker
     * @return the given argument
     */
    @Nullable
    public static <T> T forCompoundAssignment(@Nullable T lhs) {
        return lhs;
    }

    /**
     * Converts the sum produced by {@code <OP>=} expression to the result of this expression. Implementations may replace the return value with {@code null}.
     * <p>
     * The AST transformer knows the name of this method.
     *
     * @param result the result, may not implement the custom protocol
     * @param <T> the type of the expression
     * @return the result for the {@code <OP>=} expression
     */
    @Nullable
    public static <T> T toAssignmentResult(@Nullable T result) {
        if (result instanceof CompoundAssignmentResult) {
            CompoundAssignmentResult assignmentResult = (CompoundAssignmentResult) result;
            assignmentResult.assignmentComplete();
            if (assignmentResult.shouldDiscardResult()) {
                return null;
            }
        }
        return result;
    }
}
