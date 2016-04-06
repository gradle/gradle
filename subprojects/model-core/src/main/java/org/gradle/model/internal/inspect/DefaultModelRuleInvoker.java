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

import org.gradle.internal.Factory;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;

import java.lang.reflect.Modifier;

class DefaultModelRuleInvoker<I, R> implements ModelRuleInvoker<R> {
    private final WeaklyTypeReferencingMethod<I, R> method;
    private final Factory<? extends I> factory;

    DefaultModelRuleInvoker(WeaklyTypeReferencingMethod<I, R> method, Factory<? extends I> factory) {
        this.method = method;
        this.factory = factory;
    }

    @Override
    public R invoke(Object... args) {
        I instance = Modifier.isStatic(method.getModifiers()) ? null : factory.create();
        return method.invoke(instance, args);
    }
}
