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

package org.gradle.model.internal.core;

import org.gradle.internal.Cast;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ManagedSetModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    public ManagedSetModelProjection(ModelType<M> type) {
        super(type, true, true);
    }

    @Override
    protected ModelView<M> toView(final MutableModelNode modelNode, final ModelRuleDescriptor ruleDescriptor, final boolean writable) {
        return new ModelView<M>() {
            public boolean closed;

            @Override
            public ModelType<M> getType() {
                return ManagedSetModelProjection.this.getType();
            }

            @Override
            public M getInstance() {
                Class<M> clazz = getType().getConcreteClass(); // safe because we know schema must be of a concrete type
                Object view = Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[] {clazz, ManagedInstance.class}, new ManagedSetViewInvocationHandler());
                return Cast.uncheckedCast(view);
            }

            @Override
            public void close() {
                closed = true;
            }

            class ManagedSetViewInvocationHandler implements InvocationHandler {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("create")) {
                        if (!writable) {
                            throw new IllegalStateException(String.format("Cannot mutate model element '%s' of type '%s' as it is an input to rule '%s'", modelNode.getPath(), getType(), ruleDescriptor));
                        }
                        if (closed) {
                            throw new IllegalStateException(String.format("Cannot mutate model element '%s' of type '%s' used as subject of rule '%s' after the rule has completed", modelNode.getPath(), getType(), ruleDescriptor));
                        }
                    } else if (writable && !closed && DefaultManagedSet.READ_METHOD_NAMES.contains(method.getName())) {
                        throw new IllegalStateException(String.format("Cannot read contents of element '%s' of type '%s' while it's mutable", modelNode.getPath(), getType(), ruleDescriptor));
                    }
                    return method.invoke(modelNode.getPrivateData(getType()), args);
                }
            }
        };
    }
}
