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

package org.gradle.api.internal.provider.support;

import javax.annotation.Nullable;

/**
 * Support for special protocol of compound assignments ({@code +=}, {@code -=}, etc.) handling in Groovy.
 * <p>
 * Unlike Kotlin, Groovy doesn't allow overloading the compound assignment operators.
 * One has to rely on overloading the non-assignment part, i.e. {@code +} for {@code +=} when implementing that.
 * For example, Groovy always treats {@code a += b} as {@code a = a + b}.
 * <p>
 * Gradle applies a source transformation to separate these two expressions.
 * The compound assignment {@code LHS <OP>= RHS} is transformed into:
 * <pre>
 *     CompoundAssignmentSupport.finishCompoundAssignment(
 *         LHS = CompoundAssignmentSupport.forCompoundAssignment(LHS &lt;OP&gt; RHS)
 *     )
 * </pre>
 * If the result of {@code LHS <OP> RHS} implements {@link CompoundAssignmentValue}, then its lifecycle methods are invoked.
 * {@code LHS} is reassigned with the value returned by {@link #forCompoundAssignment(Object)}.
 * However, the result of the expression may be different if {@link #finishCompoundAssignment(Object)} replaces it.
 * <p>
 * <b>Important</b>: to opt into the custom protocol, this interface must be implemented by the result of the operator, not by the left-hand side operand.
 * <p>
 * <b>API stability:</b> this should be considered public API, changes should be binary compatible.
 * Plugins written in Groovy may contain references to the methods of this class.
 * <p>
 * See {@code org.gradle.groovy.scripts.internal.CompoundAssignmentTransformer} for the actual transformation.
 */
@SuppressWarnings("unused") // references are produced by the AST transform
public final class CompoundAssignmentSupport {
    public static final String FEATURE_FLAG_NAME = "org.gradle.internal.groovy-compound-assignment-enabled";

    private CompoundAssignmentSupport() {}

    public static boolean isEnabled() {
        return Boolean.getBoolean(FEATURE_FLAG_NAME);
    }

    /**
     * Adjusts the result of the compound operation before assigning it to LHS by calling {@link CompoundAssignmentValue#prepareForAssignment()}.
     * The returned value is assigned to LHS either directly (if it is a variable or a property of non-enhanced type), or by calling {@link LazyGroovySupport#setFromAnyValue(Object)}.
     * <p>
     * Because of limitations of the AST transformation, it transforms all compound assignments in code regardless of type.
     * Thus, the value may not implement SupportsCompoundAssignment.
     * In this case nothing happens and the value is returned unchanged.
     *
     * @param compoundValue the value produced by operator
     * @param <T> the type of the value, to keep the static type checker happy
     * @return the argument
     */
    @Nullable
    public static <T> T forCompoundAssignment(@Nullable T compoundValue) {
        if (isEnabled() && compoundValue instanceof CompoundAssignmentValue) {
            ((CompoundAssignmentValue) compoundValue).prepareForAssignment();
        }
        return compoundValue;
    }

    /**
     * Called after assignment is completed to produce the value for the enclosing expression.
     * Calls {@link CompoundAssignmentValue#assignmentCompleted()}.
     * If the compound assignment was a sub-expression, this method may replace its value to {@code null}
     * if {@link CompoundAssignmentValue#shouldReplaceResultWithNull()} is {@code false}.
     *
     * Because of limitations of the AST transformation, it transforms all compound assignments in code regardless of type.
     * Thus, the value may not implement SupportsCompoundAssignment.
     * In this case nothing happens and the value is returned unchanged.
     *
     * @param compoundValue the value produced by operator
     * @param <T> the type of the value, to keep the static type checker happy
     * @return the argument
     */
    @Nullable
    public static <T> T finishCompoundAssignment(@Nullable T compoundValue) {
        if (isEnabled() && compoundValue instanceof CompoundAssignmentValue) {
            CompoundAssignmentValue value = (CompoundAssignmentValue) compoundValue;
            value.assignmentCompleted();
            return value.shouldReplaceResultWithNull() ? null : compoundValue;
        }
        return compoundValue;
    }
}
