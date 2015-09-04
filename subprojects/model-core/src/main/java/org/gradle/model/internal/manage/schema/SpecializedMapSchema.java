/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

import org.gradle.model.internal.type.ModelType;

import java.lang.ref.WeakReference;

/*
    This should implement ManagedImplModelSchema.
    It doesn't because we can't quite have a node initializer for it.
    As it currently stands, initialization of nodes of this type requires a supporting instance factory.
    Based on just the schema, we don't quite know what the reference to the factory is.
    We _could_ infer it based on the component type (e.g. if ModelMap<Component> then depend on InstanceFactory<Component, String>)
    but that breaks down if there are different factories.

    The implementation of “specialised maps” needs to be tidied up at some point and this inconsistency sorted out.

    LD.
 */
public class SpecializedMapSchema<T> extends AbstractModelSchema<T> {
    private final WeakReference<Class<?>> implementationType;
    private final ModelType<?> elementType;

    public SpecializedMapSchema(ModelType<T> type, ModelType<?> elementType, Class<?> implementationType) {
        super(type);
        this.elementType = elementType;
        this.implementationType = new WeakReference<Class<?>>(implementationType);
    }

    public ModelType<?> getElementType() {
        return elementType;
    }

    public Class<?> getImplementationType() {
        return implementationType.get();
    }

    @Override
    public String toString() {
        return "model map " + getType();
    }
}
