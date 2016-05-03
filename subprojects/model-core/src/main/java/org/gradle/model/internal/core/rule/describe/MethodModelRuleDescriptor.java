/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core.rule.describe;

import com.google.common.base.Objects;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.UncheckedIOException;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

// TODO some kind of context of why the method was attached (e.g. which plugin declared the rule)
// TODO some kind of instance state for the method (might be the same as context above)
@ThreadSafe
public class MethodModelRuleDescriptor extends AbstractModelRuleDescriptor {
    private final static Cache DESCRIPTOR_CACHE = new Cache();

    private final WeaklyTypeReferencingMethod<?, ?> method;
    private String description;
    private int hashCode;

    public MethodModelRuleDescriptor(ModelType<?> target, ModelType<?> returnType, Method method) {
        this(WeaklyTypeReferencingMethod.of(target, returnType, method));
    }

    public MethodModelRuleDescriptor(WeaklyTypeReferencingMethod<?, ?> method) {
        this.method = method;
    }

    @Override
    public void describeTo(Appendable appendable) {
        try {
            appendable.append(getDescription());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getDescription() {
        if (description == null) {
            description = STRING_INTERNER.intern(getClassName() + "#" + method.getName());
        }

        return description;
    }

    private String getClassName() {
        return method.getDeclaringType().getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MethodModelRuleDescriptor that = (MethodModelRuleDescriptor) o;
        return Objects.equal(method, that.method);
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if (result != 0) {
            return result;
        }
        result = Objects.hashCode(method);
        hashCode = result;
        return result;
    }

    public static <T, R> ModelRuleDescriptor of(WeaklyTypeReferencingMethod<T, R> method) {
        return DESCRIPTOR_CACHE.get(method.getDeclaringType().getConcreteClass(), method.getName());
    }

    private static class Cache {
        private final WeakHashMap<Class<?>, CacheEntry> cached = new WeakHashMap<Class<?>, CacheEntry>();

        public synchronized ModelRuleDescriptor get(Class<?> clazz, String method) {
            CacheEntry cacheEntry = cached.get(clazz);
            if (cacheEntry == null) {
                cacheEntry = new CacheEntry(clazz);
                cached.put(clazz, cacheEntry);
            }
            return cacheEntry.get(method);
        }

        private static class CacheEntry {
            private final ModelType<?> clazzType;
            private final Map<String, MethodModelRuleDescriptor> descriptors;

            public CacheEntry(Class<?> clazz) {
                this.clazzType = ModelType.of(clazz);
                this.descriptors = new HashMap<String, MethodModelRuleDescriptor>(clazz.getDeclaredMethods().length);
            }

            public MethodModelRuleDescriptor get(String methodName) {
                MethodModelRuleDescriptor desc = descriptors.get(methodName);
                if (desc != null) {
                    return desc;
                }
                Class<?> clazz = clazzType.getConcreteClass();
                Method[] declaredMethods = clazz.getDeclaredMethods();
                Method method = null;
                for (Method declaredMethod : declaredMethods) {
                    if (declaredMethod.getName().equals(methodName)) {
                        if (method != null) {
                            throw new IllegalStateException("Class " + clazz.getName() + " has more than one method named '" + methodName + "'");
                        }
                        method = declaredMethod;
                    }
                }
                if (method == null) {
                    throw new IllegalStateException("Class " + clazz.getName() + " has no method named '" + methodName + "'");
                }
                desc = new MethodModelRuleDescriptor(ModelType.of(clazz), ModelType.returnType(method), method);
                descriptors.put(methodName, desc);
                return desc;
            }
        }
    }
}
