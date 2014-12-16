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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.internal.Cast;

import java.lang.reflect.Type;
import java.util.Arrays;

public class MethodDescription {

    private final Class<?> owner;
    private final String name;
    private final Type returnType;
    private final Type[] parameterTypes;

    public MethodDescription(Class<?> owner, String name, Type returnType, Type[] parameterTypes) {
        this.owner = owner;
        this.name = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    public static Builder name(String name) {
        return new Builder(name);
    }

    @Override
    public String toString() {
        Iterable<String> parameterTypeNames = Iterables.transform(Arrays.asList(parameterTypes), new Function<Type, String>() {
            public String apply(Type type) {
                return typeName(type);
            }
        });
        return String.format("%s %s.%s(%s)", typeName(returnType), owner.getName(), name, Joiner.on(", ").join(parameterTypeNames));
    }

    private String typeName(Type type) {
        return Class.class.isInstance(type) ? Cast.cast(Class.class, type).getName() : type.toString();
    }

    public static class Builder {

        private final String name;
        private Type returnType = Void.TYPE;
        private Class<?> owner;
        private Type[] parameterTypes = new Type[0];

        public Builder(String name) {
            this.name = name;
        }

        public Builder returns(Type returnType) {
            this.returnType = returnType;
            return this;
        }

        public Builder owner(Class<?> owner) {
            this.owner = owner;
            return this;
        }

        public Builder takes(Type[] parameterTypes) {
            this.parameterTypes = parameterTypes;
            return this;
        }

        public MethodDescription build() {
            return new MethodDescription(owner, name, returnType, parameterTypes);
        }

        public String toString() {
            return build().toString();
        }
    }

}
