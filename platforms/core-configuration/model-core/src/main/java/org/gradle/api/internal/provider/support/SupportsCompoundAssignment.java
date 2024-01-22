/*
 * Copyright 2024 the original author or authors.
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
 * Implementors of this interface support custom handling of the compound assignment operators ({@code +=, -=}) in Groovy.
 * Groovy doesn't support overloading of just compound assignments, and instead generates a combination of a base operator and an assignment.
 * For example, an expression {@code a += b} is transformed into {@code a = a + b}.
 * <p>
 * A Groovy AST transformation is used to intercept {@code +=}. An expression {@code a += b} is then transformed into:
 * {@code SupportsCompoundAssignment.unwrap(a = SupportsCompoundAssignment.wrap(a) + b)}.
 * When the {@code a} doesn't implement this interface, the functions are no-op.
 * This way you can opt into the custom protocol and provide a dedicated expression value that doesn't have to be the same value that
 * is assigned to the target.
 * <p>
 * Implementing this interface allows an object to provide a stand-in operand for the base operator invocation.
 * The stand-in can implement necessary protocol even if the class itself doesn't overload the operator.
 * <p>
 * Methods of this class are not intended to be called by the handwritten code. The transformed code relies on Groovy's
 * dynamic dispatch to resolve it correctly.
 * As the AST transformation typically doesn't know the type of the arguments, it has to emit the wrap/unwrap calls every time.
 * That's why the class provides the no-op methods.
 * <p>
 * See {@code org.gradle.groovy.scripts.internal.CompoundAssignmentTransformer} for the actual transformation.
 *
 * @param <T> the type of the stand-in
 */
public interface SupportsCompoundAssignment<T> {

    /**
     * Produces a compound assignment stand-in. The returned object will be used as left operand of the compound expression.
     *
     * @return the stand-in
     */
    T toCompoundOperand();

    /**
     * The interface that provides return value replacement.
     *
     * @param <T> the type of the replaced return value
     */
    interface Result<T> {
        /**
         * Returns the replacement for this result, to be used as a value of the compound assignment subexpression.
         * Note that this is the value of the whole {@code a += b}, it isn't the value that will be stored in {@code a}.
         * In fact, if {@code a} is a variable, then this object will be its new value.
         * <p>
         * By the time this method is called, the assignment has already happened.
         *
         * @return the replacement, can be null
         */
        @Nullable
        T unwrap();
    }

    /**
     * Retrieves the stand-in from the argument. The AST transformer knows the name of this method.
     *
     * @param lhs the original argument
     * @param <T> the stand-in type.
     * @return the stand-in
     */
    static <T> T wrap(SupportsCompoundAssignment<T> lhs) {
        return lhs.toCompoundOperand();
    }

    /**
     * No-op implementation, return the argument. The AST transformer knows the name of this method.
     * This method is called when the compound assignment expression should use the default Groovy logic.
     *
     * @param lhs the original argument
     * @return the give argument
     */
    @Nullable
    static Object wrap(@Nullable Object lhs) {
        return lhs;
    }

    /**
     * Retrieves the result replacement. The AST transformer knows the name of this method.
     *
     * @param result the original result, already assigned to the target
     * @param <T> the replacement type
     * @return the replaced result, can be null
     */
    @Nullable
    static <T> T unwrap(Result<T> result) {
        return result.unwrap();
    }

    /**
     * No-op implementation, return the given result. The AST transformer knows the name of this method.
     * This method is called when the compound assignment expression should use the default Groovy logic.
     *
     * @param result the original result
     * @return the given result
     */
    @Nullable
    static Object unwrap(@Nullable Object result) {
        return result;
    }
}
