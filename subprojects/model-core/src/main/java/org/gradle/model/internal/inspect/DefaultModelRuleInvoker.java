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

package org.gradle.model.internal.inspect;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.internal.core.ModelType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class DefaultModelRuleInvoker<I, R> implements ModelRuleInvoker<R> {
    private final JavaMethod<I, R> javaMethod;
    private final ModelType<I> source;

    DefaultModelRuleInvoker(Method method, ModelType<I> source, ModelType<R> returnType) {
        javaMethod = JavaReflectionUtil.method(source.getConcreteClass(), returnType.getConcreteClass(), method);
        this.source = source;
    }

    public R invoke(Object... args) {
        I instance = Modifier.isStatic(javaMethod.getMethod().getModifiers()) ? null : toInstance();
        return javaMethod.invoke(instance, args);
    }

    private I toInstance() {
        try {
            Class<I> concreteClass = source.getConcreteClass();
            Constructor<I> declaredConstructor = concreteClass.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            return declaredConstructor.newInstance();
        } catch (InstantiationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getTargetException());
        }
    }
}
