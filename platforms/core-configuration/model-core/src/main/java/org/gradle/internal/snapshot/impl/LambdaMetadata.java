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

package org.gradle.internal.snapshot.impl;

import java.lang.invoke.SerializedLambda;

class LambdaMetadata {

    public static LambdaMetadata from(SerializedLambda lambda, ClassLoader classLoader) {
        try {
            Class<?> capturingClass = classLoader.loadClass(lambda.getCapturingClass().replace('/', '.'));
            return new LambdaMetadata(
                capturingClass,
                lambda.getFunctionalInterfaceClass(),
                lambda.getFunctionalInterfaceMethodName(),
                lambda.getFunctionalInterfaceMethodSignature(),
                lambda.getImplMethodKind(),
                lambda.getImplClass(),
                lambda.getImplMethodName(),
                lambda.getImplMethodSignature(),
                lambda.getInstantiatedMethodType()
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    final Class<?> capturingClass;
    private final String functionalInterfaceClass;
    private final String functionalInterfaceMethodName;
    private final String functionalInterfaceMethodSignature;
    private final String implClass;
    private final String implMethodName;
    private final String implMethodSignature;
    private final int implMethodKind;
    private final String instantiatedMethodType;

    public LambdaMetadata(
        Class<?> capturingClass,
        String functionalInterfaceClass,
        String functionalInterfaceMethodName,
        String functionalInterfaceMethodSignature,
        int implMethodKind,
        String implClass,
        String implMethodName,
        String implMethodSignature,
        String instantiatedMethodType
    ) {
        this.capturingClass = capturingClass;
        this.functionalInterfaceClass = functionalInterfaceClass;
        this.functionalInterfaceMethodName = functionalInterfaceMethodName;
        this.functionalInterfaceMethodSignature = functionalInterfaceMethodSignature;
        this.implMethodKind = implMethodKind;
        this.implClass = implClass;
        this.implMethodName = implMethodName;
        this.implMethodSignature = implMethodSignature;
        this.instantiatedMethodType = instantiatedMethodType;
    }

    public SerializedLambda toSerializedLambda(Object[] capturedArgs) {
        return new SerializedLambda(
            capturingClass,
            functionalInterfaceClass,
            functionalInterfaceMethodName,
            functionalInterfaceMethodSignature,
            implMethodKind,
            implClass,
            implMethodName,
            implMethodSignature,
            instantiatedMethodType,
            capturedArgs
        );
    }
}
