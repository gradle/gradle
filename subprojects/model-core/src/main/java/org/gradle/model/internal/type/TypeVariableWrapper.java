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

import com.google.common.base.Joiner;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public class TypeVariableWrapper implements TypeVariable<GenericDeclaration>, TypeWrapper {
    private final String name;
    private final TypeWrapper[] bounds;
    private final GenericDeclaration genericDeclaration;

    public TypeVariableWrapper(String name, TypeWrapper[] bounds, GenericDeclaration genericDeclaration) {
        this.name = name;
        this.bounds = bounds;
        this.genericDeclaration = genericDeclaration;
    }

    @Override
    public Type[] getBounds() {
        return ModelType.unwrap(bounds);
    }

    @Override
    public GenericDeclaration getGenericDeclaration() {
        return genericDeclaration;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type unwrap() {
        return this;
    }

    @Override
    public String getRepresentation() {
        return toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());

        Type[] bounds = getBounds();
        if (bounds.length > 0) {
            sb.append(" extends ");
            sb.append(Joiner.on(" & ").join(bounds));
        }

        return sb.toString();
    }
}
