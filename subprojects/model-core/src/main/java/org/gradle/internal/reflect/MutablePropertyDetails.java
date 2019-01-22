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

import java.lang.reflect.Method;
import java.util.List;

class MutablePropertyDetails implements PropertyDetails {
    private final String name;
    private final MethodSet getters = new MethodSet();
    private final MethodSet setters = new MethodSet();

    MutablePropertyDetails(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Method> getGetters() {
        return getters.getValues();
    }

    @Override
    public List<Method> getSetters() {
        return setters.getValues();
    }

    void addGetter(Method method) {
        getters.add(method);
    }

    void addSetter(Method method) {
        setters.add(method);
    }
}
