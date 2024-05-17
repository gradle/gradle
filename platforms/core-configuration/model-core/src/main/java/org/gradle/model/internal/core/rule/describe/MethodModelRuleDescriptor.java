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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.UncheckedIOException;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// TODO some kind of context of why the method was attached (e.g. which plugin declared the rule)
// TODO some kind of instance state for the method (might be the same as context above)
@ThreadSafe
public class MethodModelRuleDescriptor extends AbstractModelRuleDescriptor {
    private final static Cache DESCRIPTOR_CACHE = new Cache();
    private final static Joiner PARAM_JOINER = Joiner.on(", ");
    private static final Function<ModelType<?>, String> TYPE_DISPLAYNAME_FUNCTION = new Function<ModelType<?>, String>() {
        @Override
        public String apply(ModelType<?> input) {
            return input.getDisplayName();
        }
    };

    private final WeaklyTypeReferencingMethod<?, ?> method;
    private String description;
    private int hashCode;

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
            description = createDescription();
        }
        return description;
    }

    private String createDescription() {
        return getClassName() + "#" + method.getName() + "(" + toParameterList(method.getGenericParameterTypes()) + ")";
    }

    private static String toParameterList(List<ModelType<?>> genericParameterTypes) {
        return PARAM_JOINER.join(Iterables.transform(genericParameterTypes, TYPE_DISPLAYNAME_FUNCTION));
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
        return DESCRIPTOR_CACHE.get(method);
    }

    private static class Cache {
        private final WeakHashMap<Class<?>, CacheEntry> cached = new WeakHashMap<Class<?>, CacheEntry>();

        public synchronized <T, R> ModelRuleDescriptor get(WeaklyTypeReferencingMethod<T, R> method) {
            Class<?> clazz = method.getDeclaringType().getConcreteClass();
            CacheEntry cacheEntry = cached.get(clazz);
            if (cacheEntry == null) {
                cacheEntry = new CacheEntry(clazz);
                cached.put(clazz, cacheEntry);
            }
            MethodModelRuleDescriptor methodModelRuleDescriptor = cacheEntry.get(method);
            if (methodModelRuleDescriptor == null) {
                methodModelRuleDescriptor = new MethodModelRuleDescriptor(method);
            }
            return methodModelRuleDescriptor;
        }

        private static class CacheEntry {
            private final Map<WeaklyTypeReferencingMethod<?, ?>, MethodModelRuleDescriptor> descriptors;

            public CacheEntry(Class<?> clazz) {
                this.descriptors = new HashMap<WeaklyTypeReferencingMethod<?, ?>, MethodModelRuleDescriptor>(clazz.getDeclaredMethods().length);
            }

            public <T, R> MethodModelRuleDescriptor get(WeaklyTypeReferencingMethod<T, R> weakMethod) {
                MethodModelRuleDescriptor desc = descriptors.get(weakMethod);
                if (desc != null) {
                    return desc;
                }
                desc = new MethodModelRuleDescriptor(weakMethod);
                // Only cache non-overloaded methods by name
                descriptors.put(weakMethod, desc);
                return desc;
            }
        }
    }
}
