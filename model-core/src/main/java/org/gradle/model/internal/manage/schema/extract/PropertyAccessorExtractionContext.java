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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.model.internal.asm.AsmClassGeneratorUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PropertyAccessorExtractionContext {
    private final PropertyAccessorType accessorType;
    private final Collection<Method> declaringMethods;
    private final Method mostSpecificDeclaration;
    private final String mostSpecificSignature;
    private final boolean declaredInManagedType;
    private final boolean declaredAsAbstract;
    private final Map<Class<? extends Annotation>, Annotation> annotations;

    public PropertyAccessorExtractionContext(PropertyAccessorType accessorType, Iterable<Method> declaringMethods) {
        Method mostSpecificDeclaration = ModelSchemaUtils.findMostSpecificMethod(declaringMethods);
        this.accessorType = accessorType;
        this.declaringMethods = ImmutableList.copyOf(declaringMethods);
        this.mostSpecificDeclaration = mostSpecificDeclaration;
        this.mostSpecificSignature = AsmClassGeneratorUtils.signature(mostSpecificDeclaration);
        this.declaredInManagedType = ModelSchemaUtils.isMethodDeclaredInManagedType(declaringMethods);
        this.declaredAsAbstract = Modifier.isAbstract(this.mostSpecificDeclaration.getModifiers());
        this.annotations = collectAnnotations(declaringMethods);
    }

    private Map<Class<? extends Annotation>, Annotation> collectAnnotations(Iterable<Method> methods) {
        Map<Class<? extends Annotation>, Annotation> annotations = Maps.newLinkedHashMap();
        for (Method method : methods) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                // Make sure more specific annotation doesn't get overwritten with less specific one
                if (!annotations.containsKey(annotation.annotationType())) {
                    annotations.put(annotation.annotationType(), annotation);
                }
            }
        }
        return Collections.unmodifiableMap(annotations);
    }

    public PropertyAccessorType getAccessorType() {
        return accessorType;
    }

    public Collection<Method> getDeclaringMethods() {
        return declaringMethods;
    }

    public Method getMostSpecificDeclaration() {
        return mostSpecificDeclaration;
    }

    public String getMostSpecificSignature() {
        return mostSpecificSignature;
    }

    public boolean isDeclaredInManagedType() {
        return declaredInManagedType;
    }

    public boolean isDeclaredAsAbstract() {
        return declaredAsAbstract;
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return annotations.containsKey(annotationType);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return Cast.uncheckedCast(annotations.get(annotationType));
    }

    public Collection<Annotation> getAnnotations() {
        return annotations.values();
    }

    public List<Method> getGetters() {
        List<Method> getters;
        if (mostSpecificDeclaration.getReturnType()==Boolean.TYPE) {
            getters = Lists.newArrayList();
            for (Method getter : declaringMethods) {
                if (Proxy.isProxyClass(getter.getDeclaringClass())) {
                    continue;
                }
                getters.add(getter);
            }
        } else {
            getters = Collections.singletonList(mostSpecificDeclaration);
        }
        return getters;
    }

    @Override
    public String toString() {
        return String.format("%s.%s()/%s", mostSpecificDeclaration.getDeclaringClass().getSimpleName(), mostSpecificDeclaration.getName(), accessorType);
    }
}
