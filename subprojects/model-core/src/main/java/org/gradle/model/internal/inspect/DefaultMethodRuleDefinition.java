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

package org.gradle.model.internal.inspect;

import com.google.common.collect.ImmutableList;
import org.gradle.api.specs.Spec;
import org.gradle.model.Path;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import static org.gradle.util.CollectionUtils.findFirst;

public class DefaultMethodRuleDefinition<T, R> implements MethodRuleDefinition<R> {
    private final Method method;
    private final ModelType<T> instanceType;
    private final ModelType<R> returnType;

    private DefaultMethodRuleDefinition(Method method, ModelType<T> instanceType, ModelType<R> returnType) {
        this.method = method;
        this.instanceType = instanceType;
        this.returnType = returnType;
    }
    
    public static <T, R> MethodRuleDefinition<R> create(Class<T> source, Method method) {
        @SuppressWarnings("unchecked") ModelType<R> returnType = (ModelType<R>) ModelType.of(method.getGenericReturnType());
        return new DefaultMethodRuleDefinition<T, R>(method, ModelType.of(source), returnType);
    }

    public String getMethodName() {
        return method.getName();
    }

    public ModelType<R> getReturnType() {
        return returnType;
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return method.getAnnotation(annotationType);
    }

    public ModelRuleDescriptor getDescriptor() {
        return new MethodModelRuleDescriptor(method);
    }

    public ModelRuleInvoker<R> getRuleInvoker() {
        return new DefaultModelRuleInvoker<T, R>(method, instanceType, returnType);
    }

    public List<ModelReference<?>> getReferences() {
        Type[] types = method.getGenericParameterTypes();
        ImmutableList.Builder<ModelReference<?>> inputBindingBuilder = ImmutableList.builder();
        for (int i = 0; i < types.length; i++) {
            Type paramType = types[i];
            Annotation[] paramAnnotations = method.getParameterAnnotations()[i];
            inputBindingBuilder.add(reference(paramType, paramAnnotations, i));
        }
        return inputBindingBuilder.build();
    }

    private ModelReference<?> reference(Type type, Annotation[] annotations, int i) {
        Path pathAnnotation = (Path) findFirst(annotations, new Spec<Annotation>() {
            public boolean isSatisfiedBy(Annotation element) {
                return element.annotationType().equals(Path.class);
            }
        });
        String path = pathAnnotation == null ? null : pathAnnotation.value();
        ModelType<?> cast = ModelType.of(type);
        return ModelReference.of(path == null ? null : ModelPath.path(path), cast, String.format("parameter %s", i + 1));
    }

}
