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
 * Implementing this interface allows an object to provide a stand-in operand for the base operator invocation.
 * The stand-in can implement necessary protocol even if the class itself doesn't overload the operator.
 */
public interface SupportsCompoundAssignment<T> {
    T toCompoundOperand();

    static <T> T wrap(SupportsCompoundAssignment<T> lhs) {
        return lhs.toCompoundOperand();
    }

    @Nullable
    static Object wrap(@Nullable Object lhs) {
        return lhs;
    }
}
