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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

@ThreadSafe
class DefaultModelRuleInvoker<I, R> implements ModelRuleInvoker<R> {
    private final WeaklyTypeReferencingMethod<I, R> method;

    DefaultModelRuleInvoker(WeaklyTypeReferencingMethod<I, R> method) {
        this.method = method;
    }

    public R invoke(Object... args) {
        I instance = Modifier.isStatic(method.getModifiers()) ? null : toInstance();
        return method.invoke(instance, args);
    }

    private I toInstance() {
        try {
            Class<I> concreteClass = method.getTarget().getConcreteClass();
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
