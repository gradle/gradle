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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.MethodDescription;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

// TODO some kind of context of why the method was attached (e.g. which plugin declared the rule)
// TODO some kind of instance state for the method (might be the same as context above)
@ThreadSafe
public class MethodModelRuleDescriptor extends AbstractModelRuleDescriptor {

    private final WeaklyTypeReferencingMethod<?, ?> method;
    private String description;

    public MethodModelRuleDescriptor(ModelType<?> target, ModelType<?> returnType, Method method) {
        this(WeaklyTypeReferencingMethod.of(target, returnType, method));
    }

    public MethodModelRuleDescriptor(WeaklyTypeReferencingMethod<?, ?> method) {
        this.method = method;
    }

    public void describeTo(Appendable appendable) {
        try {
            appendable.append(getDescription());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getDescription() {
        if (description == null) {
            description = MethodDescription.name(method.getName())
                    .owner(method.getDeclaringClass())
                    .takes(method.getGenericParameterTypes())
                    .toString();
        }

        return description;
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

        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    public static ModelRuleDescriptor of(Class<?> clazz, final String methodName) {
        List<Method> methodsOfName = CollectionUtils.filter(clazz.getDeclaredMethods(), new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                return element.getName().equals(methodName);
            }
        });

        if (methodsOfName.isEmpty()) {
            throw new IllegalStateException("Class " + clazz.getName() + " has no method named '" + methodName + "'");
        }

        if (methodsOfName.size() > 1) {
            throw new IllegalStateException("Class " + clazz.getName() + " has more than one method named '" + methodName + "'");
        }

        Method method = methodsOfName.get(0);
        return of(clazz, method);
    }

    public static ModelRuleDescriptor of(Class<?> clazz, Method method) {
        return new MethodModelRuleDescriptor(ModelType.of(clazz), ModelType.returnType(method), method);
    }
}
