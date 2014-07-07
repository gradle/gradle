/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal;

import com.google.common.reflect.TypeToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.Model;
import org.gradle.model.ModelPath;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;

public class ModelRuleInspector {

    // TODO should either return the extracted rule, or metadata about the extraction (i.e. for reporting etc.)
    public <T> void inspect(Class<T> source, ModelRegistry modelRegistry) {
        Method[] methods = source.getDeclaredMethods();
        for (Method method : methods) {
            Model modelAnnotation = method.getAnnotation(Model.class);
            if (modelAnnotation != null) {
                if (method.getParameterTypes().length > 0) {
                    throw new IllegalArgumentException("@Model rules cannot take arguments");
                }

                // TODO other validations on method: synthetic, bridge methods, varargs, abstract, native

                // TODO validate model name
                String modelName = determineModelName(modelAnnotation, method);

                // TODO validate the return type (generics?)
                TypeToken<?> returnType = TypeToken.of(method.getGenericReturnType());

                doRegisterCreation(source, method, returnType, modelName, modelRegistry);
            }
        }
    }

    private <T, R> void doRegisterCreation(final Class<T> source, final Method method, final TypeToken<R> returnType, final String modelName, ModelRegistry modelRegistry) {
        @SuppressWarnings("unchecked") final Class<T> clazz = (Class<T>) source.getClass();
        @SuppressWarnings("unchecked") Class<R> returnTypeClass = (Class<R>) returnType.getRawType();
        final JavaMethod<T, R> methodWrapper = JavaReflectionUtil.method(clazz, returnTypeClass, method);

        modelRegistry.create(modelName, Collections.<String>emptyList(), new ModelCreator<R>() {

            public ModelReference<R> getReference() {
                return new ModelReference<R>(new ModelPath(modelName), new ModelType<R>(returnType));
            }

            public R create(Inputs inputs) {
                T instance = Modifier.isStatic(method.getModifiers()) ? null : toInstance(source);
                // ignore inputs, we know they're empty
                return methodWrapper.invoke(instance);
            }
        });
    }

    private static <T> T toInstance(Class<T> source) {
        try {
            return source.newInstance();
        } catch (InstantiationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String determineModelName(Model modelAnnotation, Method method) {
        String annotationValue = modelAnnotation.value();
        if (annotationValue == null || annotationValue.isEmpty()) {
            return method.getName();
        } else {
            return annotationValue;
        }
    }

}
