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

package org.gradle.internal.reflect;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

class MutablePropertyDetails implements PropertyDetails {
    private final String name;
    private final MethodSet getters = new MethodSet();
    private final MethodSet setters = new MethodSet();
    private Field field;

    MutablePropertyDetails(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<Method> getGetters() {
        return getters.getValues();
    }

    @Override
    public Collection<Method> getSetters() {
        return setters.getValues();
    }

    @Nullable
    @Override
    public Field getBackingField() {
        return field;
    }

    void addGetter(Method method) {
        getters.add(method);
    }

    void addSetter(Method method) {
        setters.add(method);
    }

    void field(Field field) {
        if (!getters.isEmpty()) {
            this.field = field;
        }
    }
}
