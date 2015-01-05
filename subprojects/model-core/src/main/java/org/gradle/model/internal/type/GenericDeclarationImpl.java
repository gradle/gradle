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

package org.gradle.model.internal.type;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.List;

public class GenericDeclarationImpl implements GenericDeclaration {

    List<TypeVariable<?>> typeParameters = Lists.newArrayList();

    public void add(TypeVariable<?> typeVariable) {
        typeParameters.add(typeVariable);
    }

    @Override
    public TypeVariable<?>[] getTypeParameters() {
        ModelType<TypeVariable<?>> typeVariableType = new ModelType<TypeVariable<?>>() {
        };
        return Iterables.toArray(typeParameters, typeVariableType.getConcreteClass());
    }
}
