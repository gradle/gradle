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

package org.gradle.api.internal.groovy.support

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Helper to test binary compatibility of compound assignment helper classes.
 */
trait CompoundAssignmentBinaryCompatibilityFixture {
    static final String OP_PLUS = "plus"

    void assertHasForCompoundAssignmentMethod(Class<?> extensionClass, Class<?> receiverType, Class<?> returnType) {
        assertHasExtensionMethod(extensionClass, CompoundAssignmentTransformer.FOR_COMPOUND_ASSIGNMENT_METHOD_NAME, receiverType, returnType)
    }

    void assertHasToAssignmentResultMethod(Class<?> extensionClass, Class<?> receiverType, Class<?> returnType) {
        assertHasExtensionMethod(extensionClass, CompoundAssignmentTransformer.TO_ASSIGNMENT_RESULT_METHOD_NAME, receiverType, returnType)
    }

    void assertHasExtensionMethod(Class<?> extensionClass, String methodName, Class<?> receiverType, Class<?> returnType) {
        Method method
        try {
            method = extensionClass.getDeclaredMethod(methodName, receiverType)
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "Could not find public static method ${returnType.name} ${extensionClass.name}.${methodName}(${receiverType.name})`", e)
        }

        assert isStatic(method)
        assert isPublic(method)
        assert method.returnType == returnType
    }

    void assertHasOperator(Class<?> receiverType, String operatorMethod, Class<?> rhsType, Class<?> returnType) {
        Method method
        try {
            method = receiverType.getDeclaredMethod(operatorMethod, rhsType)
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                "Could not find operator public method ${returnType.name} ${receiverType.name}.${operatorMethod}(${rhsType.name})`", e)
        }

        assert !isStatic(method)
        assert isPublic(method)
        assert method.returnType == returnType
    }

    private boolean isStatic(Method method) {
        return (method.modifiers & Modifier.STATIC) != 0
    }

    private boolean isPublic(Method method) {
        return (method.modifiers & Modifier.PUBLIC) != 0
    }
}
