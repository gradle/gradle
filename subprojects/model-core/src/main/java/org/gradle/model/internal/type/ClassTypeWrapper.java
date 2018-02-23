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
import java.util.ArrayList;

class ClassTypeWrapper implements TypeWrapper {
    private final WeakReference<Class<?>> reference;
    private final int hashCode;

    public ClassTypeWrapper(Class<?> clazz) {
        this.reference = new WeakReference<Class<?>>(clazz);
        hashCode = clazz.hashCode();
    }

    public Class<?> unwrap() {
        return reference.get();
    }

    @Override
    public Class<?> getRawClass() {
        return unwrap();
    }

    @Override
    public boolean isAssignableFrom(TypeWrapper wrapper) {
        return unwrap().isAssignableFrom(wrapper.getRawClass());
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ClassTypeWrapper other = (ClassTypeWrapper) obj;
        return unwrap().equals(other.unwrap());
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        builder.add(unwrap());
    }

    @Override
    public String getRepresentation(boolean full) {
        try {
            return tryToGetRepresentation(full);
        } catch (NoClassDefFoundError ignore) {
            // This happens for IBM JDK 6 for nested interfaces -- see https://issues.apache.org/jira/browse/GROOVY-7010
            // Let's try to return something as close as possible to the intended value
            Class<?> clazz = unwrap();
            Package pkg = clazz.getPackage();
            int pkgPrefixLength = pkg == null ? 0 : pkg.getName().length() + 1;
            String simpleName = clazz.getName().substring(pkgPrefixLength).replace('$', '.');
            return full && pkg != null
                ? pkg.getName() + "." + simpleName
                : simpleName;
        }
    }

    private String tryToGetRepresentation(boolean full) {
        ArrayList<Class<?>> classChain = getEnclosingClassChain();

        int topLevelIndex = classChain.size() - 1;
        Class<?> topLevelClass = classChain.get(topLevelIndex);

        StringBuilder representation = new StringBuilder();
        representation.append(
            full ? topLevelClass.getName() : topLevelClass.getSimpleName());

        for (int i = topLevelIndex - 1; i >= 0; i--) {
            representation.append('.');
            representation.append(classChain.get(i).getSimpleName());
        }
        return representation.toString();
    }

    private ArrayList<Class<?>> getEnclosingClassChain() {
        ArrayList<Class<?>> classChain = new ArrayList<Class<?>>();
        Class<?> clazz = unwrap();
        do {
            classChain.add(clazz);
            clazz = clazz.getEnclosingClass();
        } while (clazz != null);
        return classChain;
    }
}
