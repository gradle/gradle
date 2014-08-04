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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class DefaultModelRuleInvoker implements ModelRuleInvoker {
    private final Method method;

    DefaultModelRuleInvoker(Method method) {
        this.method = method;
    }

    public Object invoke(Object... args) {
        Object instance = Modifier.isStatic(method.getModifiers()) ? null : toInstance(method.getDeclaringClass());

        method.setAccessible(true);

        try {
            return method.invoke(instance, args);
        } catch (Exception e) {
            Throwable t = e;
            if (t instanceof InvocationTargetException) {
                t = e.getCause();
            }

            throw UncheckedException.throwAsUncheckedException(t);
        }
    }

    private <T> T toInstance(Class<T> source) {
        try {
            Constructor<T> declaredConstructor = source.getDeclaredConstructor();
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
