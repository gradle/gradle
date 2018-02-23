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
import java.util.*;

class MethodSet implements Iterable<Method> {
    private final Set<MethodSignature> signatures = new HashSet<MethodSignature>();
    private final List<Method> methods = new ArrayList<Method>();

    /**
     * @return true if the method was added, false if not
     */
    public boolean add(Method method) {
        MethodSignature methodSignature = new MethodSignature(method.getName(), method.getParameterTypes());
        if (signatures.add(methodSignature)) {
            methods.add(method);
            return true;
        }
        return false;
    }

    public Iterator<Method> iterator() {
        return methods.iterator();
    }

    public List<Method> getValues() {
        return methods;
    }

    public boolean isEmpty() {
        return methods.isEmpty();
    }
}
