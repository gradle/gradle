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

import javax.annotation.Nullable;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

class SerializedLambdaQueries {
    private static final String GENERATED_LAMBDA_CLASS_SUFFIX = "$$Lambda";

    public static boolean isLambdaClass(Class<?> type) {
        return type.isSynthetic() && isLambdaClassName(type.getName());
    }

    public static boolean isLambdaClassName(String className) {
        return className.contains(GENERATED_LAMBDA_CLASS_SUFFIX);
    }

    public static Optional<SerializedLambda> serializedLambdaFor(@Nullable Object lambda) {
        if (!(lambda instanceof Serializable)) {
            return Optional.empty();
        }
        for (Class<?> lambdaClass = lambda.getClass(); lambdaClass != null; lambdaClass = lambdaClass.getSuperclass()) {
            try {
                Method replaceMethod = lambdaClass.getDeclaredMethod("writeReplace");
                replaceMethod.setAccessible(true);
                Object serializedForm = replaceMethod.invoke(lambda);
                if (serializedForm instanceof SerializedLambda) {
                    return Optional.of((SerializedLambda) serializedForm);
                } else {
                    return Optional.empty();
                }
            } catch (NoSuchMethodException e) {
                // continue
            } catch (InvocationTargetException | IllegalAccessException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
