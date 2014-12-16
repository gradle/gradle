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

package org.gradle.model.internal.manage.instance.strategy;

import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ManagedModelElement;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

public class StructModelInstantiator implements ModelInstantiatorStrategy {

    private final ManagedProxyFactory proxyFactory;

    public StructModelInstantiator(ManagedProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    public <T> T newInstance(ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator instantiator) {
        if (schema.getKind().equals(ModelSchema.Kind.STRUCT)) {
            Class<T> concreteType = schema.getType().getConcreteClass();
            ManagedModelElement<T> element = new ManagedModelElement<T>(schema, schemaStore, instantiator);
            return proxyFactory.createProxy(element.getState(), concreteType.getClassLoader(), concreteType, ManagedInstance.class);
        } else {
            return null;
        }

    }

}
