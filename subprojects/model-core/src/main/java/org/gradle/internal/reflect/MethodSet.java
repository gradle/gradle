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

import com.google.common.collect.Maps;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.unmodifiableCollection;

public class MethodSet implements Iterable<Method> {
    private final Map<MethodKey, Method> methods = Maps.newLinkedHashMap();

    public void add(Method method) {
        methods.putIfAbsent(new MethodKey(method), method);
    }

    @Override
    public Iterator<Method> iterator() {
        return getValues().iterator();
    }

    public Collection<Method> getValues() {
        return unmodifiableCollection(methods.values());
    }

    public boolean isEmpty() {
        return methods.isEmpty();
    }

    static class MethodKey {
        private final Method method;
        private final Class<?>[] parameterTypes;

        private MethodKey(Method method) {
            this.method = method;
            this.parameterTypes = method.getParameterTypes(); // avoid clone
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            MethodKey that = (MethodKey) obj;
            return Objects.equals(method.getName(), that.method.getName())
                && Objects.equals(method.getReturnType(), that.method.getReturnType())
                && Arrays.equals(parameterTypes, that.parameterTypes);

        }

        @Override
        public int hashCode() {
            return Objects.hash(method.getName(), parameterTypes.length);
        }
    }
}
