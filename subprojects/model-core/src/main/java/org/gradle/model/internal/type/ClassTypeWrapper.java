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

import com.google.common.collect.ImmutableList;

import java.lang.ref.WeakReference;

class ClassTypeWrapper implements TypeWrapper {
    private final WeakReference<Class<?>> reference;

    public ClassTypeWrapper(Class<?> clazz) {
        this.reference = new WeakReference<Class<?>>(clazz);
    }

    @Override
    public Class<?> unwrap() {
        return reference.get();
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        builder.add(unwrap());
    }

    @Override
    public String getRepresentation(boolean full) {
        if (full) {
            return unwrap().getName();
        } else {
            StringBuffer sb = new StringBuffer();
            Class<?> clazz = unwrap();
            sb.append(clazz.getSimpleName());
            for (Class<?> c = clazz.getEnclosingClass(); c != null; c = c.getEnclosingClass()) {
                sb.insert(0, '.');
                sb.insert(0, c.getSimpleName());
            }
            return sb.toString();
        }
    }
}
